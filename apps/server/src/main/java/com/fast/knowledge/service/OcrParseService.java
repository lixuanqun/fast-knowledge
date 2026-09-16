package com.fast.knowledge.service;

import com.fast.knowledge.ai.port.VisionPort;
import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.model.entity.KbDocument;
import com.fast.knowledge.storage.StorageProvider;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Base64;
import java.util.Set;

/**
 * WP2 扫描件解析 — 无文本层 PDF / 图片文档，用视觉模型（qwen-vl 系列）逐页转 Markdown。
 *
 * <p>触发条件：抽取文本过短（PDF 疑似扫描件）或文件本身是图片。
 * 解析产物逐页存入对象存储 knowledge/parsed/{docId}/page-{n}.md（可追溯、可重跑）。
 * 页数超过 {@code knowledge.ingest.ocr-max-pages} 截断并标注，费用护栏由调用方通过开关控制。
 */
@Slf4j
@Service
public class OcrParseService {

    /** 抽取文本低于该长度视为无文本层（疑似扫描件） */
    private static final int SCAN_TEXT_THRESHOLD = 50;
    private static final Set<String> IMAGE_TYPES = Set.of("jpg", "jpeg", "png", "webp", "bmp");
    private static final String EXTRACT_PROMPT = """
            你是文档数字化工程师。请把图片中的全部内容完整转写为 Markdown：
            1. 正文用 Markdown 语法（标题用 #，列表、加粗按原样）；
            2. 表格转 Markdown 表格，保持行列对应；
            3. 印章、签名、页眉页脚水印等非正文元素跳过；
            4. 只输出转写内容，不要任何解释、前后缀或代码块标记；无法辨认的字用 ¿ 表示。
            """;

    private final VisionPort visionPort;
    private final StorageProvider storageProvider;
    private final KnowledgeProperties properties;

    public OcrParseService(VisionPort visionPort, StorageProvider storageProvider, KnowledgeProperties properties) {
        this.visionPort = visionPort;
        this.storageProvider = storageProvider;
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.getIngest().isOcrEnabled();
    }

    /** 是否应走 OCR：图片文档直接是；PDF 抽取文本过短视为扫描件 */
    public boolean isScanCandidate(KbDocument doc, String extractedText) {
        String type = doc.getFileType() != null ? doc.getFileType().toLowerCase() : "";
        if (IMAGE_TYPES.contains(type)) {
            return true;
        }
        return "pdf".equals(type) && (extractedText == null || extractedText.strip().length() < SCAN_TEXT_THRESHOLD);
    }

    /**
     * 解析为 Markdown 全文。任一页失败即抛异常（调用方按索引失败处理，可重试）。
     */
    public String parseToMarkdown(KbDocument doc) throws Exception {
        String type = doc.getFileType() != null ? doc.getFileType().toLowerCase() : "";
        if (IMAGE_TYPES.contains(type)) {
            String md = askImageFile(doc, type);
            savePageArtifact(doc.getId(), 1, md);
            log.info("OCR 解析完成 docId={}（图片）长度={}", doc.getId(), md.length());
            return md;
        }
        return parsePdf(doc);
    }

    private String parsePdf(KbDocument doc) throws Exception {
        int maxPages = Math.max(1, properties.getIngest().getOcrMaxPages());
        int dpi = Math.max(72, properties.getIngest().getOcrDpi());
        byte[] pdfBytes;
        try (InputStream in = storageProvider.openInputStream(doc.getFilePath())) {
            pdfBytes = in.readAllBytes();
        }
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int totalPages = document.getNumberOfPages();
            int pages = Math.min(totalPages, maxPages);
            if (totalPages > maxPages) {
                log.warn("docId={} 共 {} 页，超过 OCR 上限 {}，只解析前 {} 页", doc.getId(), totalPages, maxPages, maxPages);
            }
            StringBuilder full = new StringBuilder();
            for (int i = 0; i < pages; i++) {
                BufferedImage image = renderer.renderImage(i, dpi / 72f);
                byte[] png = toPng(image);
                String md = visionPort.askAboutImage(
                        Base64.getEncoder().encodeToString(png), "image/png", EXTRACT_PROMPT);
                if (md == null || md.isBlank()) {
                    throw new IllegalStateException("第 " + (i + 1) + " 页视觉模型返回空结果");
                }
                md = md.strip();
                savePageArtifact(doc.getId(), i + 1, md);
                full.append("\n\n<!-- page ").append(i + 1).append(" -->\n\n").append(md);
                log.info("OCR 解析 docId={} 页 {}/{} 完成，长度 {}", doc.getId(), i + 1, pages, md.length());
            }
            if (totalPages > maxPages) {
                full.append("\n\n> 注：文档共 ").append(totalPages)
                        .append(" 页，超过单文档解析上限 ").append(maxPages).append(" 页，其余页未解析。\n");
            }
            return full.toString().strip();
        }
    }

    private String askImageFile(KbDocument doc, String type) throws Exception {
        String mime = switch (type) {
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "bmp" -> "image/bmp";
            default -> "image/jpeg";
        };
        byte[] bytes;
        try (InputStream in = storageProvider.openInputStream(doc.getFilePath())) {
            bytes = in.readAllBytes();
        }
        String md = visionPort.askAboutImage(Base64.getEncoder().encodeToString(bytes), mime, EXTRACT_PROMPT);
        if (md == null || md.isBlank()) {
            throw new IllegalStateException("视觉模型返回空结果");
        }
        return md.strip();
    }

    /** 解析产物入对象存储：knowledge/parsed/{docId}/page-{n}.md（可追溯、可重跑） */
    private void savePageArtifact(Long docId, int page, String markdown) {
        try {
            storageProvider.storeAsset("knowledge/parsed/" + docId + "/page-" + page + ".md",
                    markdown.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            // 产物存储失败不影响索引主流程
            log.warn("OCR 产物存储失败 docId={} page={}: {}", docId, page, e.getMessage());
        }
    }

    private byte[] toPng(BufferedImage image) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
