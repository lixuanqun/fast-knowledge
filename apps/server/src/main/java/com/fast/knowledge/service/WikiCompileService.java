package com.fast.knowledge.service;

import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.mapper.DocumentMapper;
import com.fast.knowledge.mapper.WikiCompileTaskMapper;
import com.fast.knowledge.mapper.WikiPageMapper;
import com.fast.knowledge.model.entity.KbDocument;
import com.fast.knowledge.model.entity.WikiCompileTask;
import com.fast.knowledge.model.entity.WikiPage;
import com.fast.knowledge.storage.StorageProvider;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class WikiCompileService {

    private static final String WIKI_SYSTEM = """
            你是企业知识库 Wiki 编译助手。根据原始文档内容，生成结构化 Markdown 知识页：
            - 包含清晰标题与要点列表
            - 保留文号、制度条款等关键信息（若有）
            - 文末注明来源文档 ID
            使用简体中文，不要编造原文没有的内容。""";

    private final KnowledgeProperties properties;
    private final DocumentMapper documentMapper;
    private final WikiPageMapper wikiPageMapper;
    private final WikiCompileTaskMapper wikiCompileTaskMapper;
    private final StorageProvider storageProvider;
    private final ChatModel chatModel;
    private final Tika tika = new Tika();

    public WikiCompileService(KnowledgeProperties properties,
                              DocumentMapper documentMapper,
                              WikiPageMapper wikiPageMapper,
                              WikiCompileTaskMapper wikiCompileTaskMapper,
                              StorageProvider storageProvider,
                              ChatModel chatModel) {
        this.properties = properties;
        this.documentMapper = documentMapper;
        this.wikiPageMapper = wikiPageMapper;
        this.wikiCompileTaskMapper = wikiCompileTaskMapper;
        this.storageProvider = storageProvider;
        this.chatModel = chatModel;
    }

    @Async("indexExecutor")
    public void scheduleCompile(Long documentId) {
        if (!properties.getWiki().isEnabled()) {
            return;
        }
        KbDocument doc = documentMapper.selectById(documentId);
        if (doc == null) {
            return;
        }
        WikiCompileTask existing = wikiCompileTaskMapper.findByDocumentId(documentId);
        if (existing == null) {
            WikiCompileTask task = new WikiCompileTask();
            task.setKbId(doc.getKbId());
            task.setDocumentId(documentId);
            task.setStatus("PENDING");
            wikiCompileTaskMapper.insert(task);
        } else if (!"PENDING".equals(existing.getStatus()) && !"FAILED".equals(existing.getStatus())) {
            return;
        } else {
            existing.setStatus("PENDING");
            existing.setErrorMsg(null);
            wikiCompileTaskMapper.updateById(existing);
        }
        try {
            compileDocument(documentId);
        } catch (Exception e) {
            log.warn("Wiki 编译失败 docId={}: {}", documentId, e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 45000)
    public void pollPendingWikiTasks() {
        if (!properties.getWiki().isEnabled()) {
            return;
        }
        for (WikiCompileTask task : wikiCompileTaskMapper.findPending(3)) {
            try {
                compileDocument(task.getDocumentId());
            } catch (Exception e) {
                log.warn("Wiki 编译任务失败 taskId={}: {}", task.getId(), e.getMessage());
            }
        }
    }

    @Transactional
    public void compileDocument(Long documentId) throws Exception {
        KbDocument doc = documentMapper.selectById(documentId);
        if (doc == null) {
            return;
        }
        WikiCompileTask task = wikiCompileTaskMapper.findByDocumentId(documentId);
        if (task != null) {
            task.setStatus("COMPILING");
            wikiCompileTaskMapper.updateById(task);
        }
        try {
            String text = extractText(doc);
            String sourceHint = buildSourceHint(doc);
            String userPrompt = sourceHint + "\n\n原文摘录：\n" + truncate(text, 12000);
            String contentMd = chatModel.chat(SystemMessage.from(WIKI_SYSTEM), UserMessage.from(userPrompt))
                    .aiMessage()
                    .text();

            String slug = "doc-" + documentId;
            WikiPage page = wikiPageMapper.findByKbAndSlug(doc.getKbId(), slug);
            if (page == null) {
                page = new WikiPage();
                page.setKbId(doc.getKbId());
                page.setSlug(slug);
                page.setTitle(doc.getTitle());
                page.setContentMd(contentMd);
                page.setSourceDocIds(String.valueOf(documentId));
                page.setVersion(1);
                page.setStatus(properties.getWiki().isAutoPublish() ? "PUBLISHED" : "DRAFT");
                wikiPageMapper.insert(page);
            } else {
                page.setTitle(doc.getTitle());
                page.setContentMd(contentMd);
                page.setSourceDocIds(String.valueOf(documentId));
                page.setVersion(page.getVersion() != null ? page.getVersion() + 1 : 1);
                if (properties.getWiki().isAutoPublish()) {
                    page.setStatus("PUBLISHED");
                }
                wikiPageMapper.updateById(page);
            }

            if (task != null) {
                task.setStatus("DONE");
                task.setErrorMsg(null);
                wikiCompileTaskMapper.updateById(task);
            }
        } catch (Exception e) {
            if (task != null) {
                task.setStatus("FAILED");
                task.setErrorMsg(truncate(e.getMessage(), 500));
                wikiCompileTaskMapper.updateById(task);
            }
            throw e;
        }
    }

    private String extractText(KbDocument doc) throws Exception {
        String fileType = doc.getFileType() != null ? doc.getFileType().toLowerCase() : "";
        try (InputStream in = storageProvider.openInputStream(doc.getFilePath())) {
            if ("txt".equals(fileType) || "md".equals(fileType)) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            return tika.parseToString(in);
        }
    }

    private String buildSourceHint(KbDocument doc) {
        StringBuilder sb = new StringBuilder("文档：《").append(doc.getTitle()).append("》");
        if (doc.getDocNo() != null && !doc.getDocNo().isBlank()) {
            sb.append("，文号：").append(doc.getDocNo());
        }
        if (doc.getDocType() != null && !doc.getDocType().isBlank()) {
            sb.append("，类型：").append(doc.getDocType());
        }
        if (doc.getDepartment() != null && !doc.getDepartment().isBlank()) {
            sb.append("，部门：").append(doc.getDepartment());
        }
        return sb.toString();
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
