package com.fast.knowledge.service;

import com.fast.knowledge.model.entity.KbDocument;
import com.fast.knowledge.storage.StorageProvider;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 文档文本提取服务 — 统一封装 Tika 解析逻辑，供索引入口与文档预览复用。
 * <p>从 DocumentIngestService / DocumentService 中抽取，消除重复的 extractText 实现。
 */
@Service
public class TextExtractionService {

    private static final int PREVIEW_MAX_CHARS = 200_000;

    private final StorageProvider storageProvider;
    private final Tika tika;

    public TextExtractionService(StorageProvider storageProvider, Tika tika) {
        this.storageProvider = storageProvider;
        this.tika = tika;
    }

    /** 全文提取（用于索引） */
    public String extractFullText(KbDocument doc) throws Exception {
        String fileType = doc.getFileType() != null ? doc.getFileType().toLowerCase() : "";
        try (InputStream in = storageProvider.openInputStream(doc.getFilePath())) {
            if ("txt".equals(fileType) || "md".equals(fileType)) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            return tika.parseToString(in);
        }
    }

    /** 带截断的预览提取（用于前端文档预览） */
    public ExtractedPreview extractPreview(KbDocument doc) throws Exception {
        String fileType = doc.getFileType() != null ? doc.getFileType().toLowerCase() : "";
        String content;
        String mode;
        try (InputStream in = storageProvider.openInputStream(doc.getFilePath())) {
            if ("txt".equals(fileType) || "md".equals(fileType)) {
                content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                mode = "raw";
            } else {
                content = tika.parseToString(in);
                mode = "extracted";
            }
        }
        boolean truncated = content.length() > PREVIEW_MAX_CHARS;
        return new ExtractedPreview(
                truncated ? content.substring(0, PREVIEW_MAX_CHARS) : content,
                mode,
                content.length(),
                truncated
        );
    }

    public record ExtractedPreview(String content, String mode, int totalLength, boolean truncated) {}
}
