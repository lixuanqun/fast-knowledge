package com.fast.knowledge.service;

import com.fast.knowledge.common.StringUtils;
import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.mapper.DocumentMapper;
import com.fast.knowledge.mapper.WikiCompileTaskMapper;
import com.fast.knowledge.mapper.WikiPageMapper;
import com.fast.knowledge.model.entity.KbDocument;
import com.fast.knowledge.model.entity.WikiCompileTask;
import com.fast.knowledge.model.entity.WikiPage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final TextExtractionService textExtractionService;
    private final ChatModel chatModel;
    private final AuditLogService auditLogService;
    /** 注入自身代理以确保 protected @Transactional 方法 AOP 生效 */
    private WikiCompileService self;

    public WikiCompileService(KnowledgeProperties properties,
                              DocumentMapper documentMapper,
                              WikiPageMapper wikiPageMapper,
                              WikiCompileTaskMapper wikiCompileTaskMapper,
                              TextExtractionService textExtractionService,
                              ChatModel chatModel,
                              AuditLogService auditLogService,
                              @Lazy WikiCompileService self) {
        this.properties = properties;
        this.documentMapper = documentMapper;
        this.wikiPageMapper = wikiPageMapper;
        this.wikiCompileTaskMapper = wikiCompileTaskMapper;
        this.textExtractionService = textExtractionService;
        this.chatModel = chatModel;
        this.auditLogService = auditLogService;
        this.self = self;
    }

    /**
     * 调度编译任务 — 由 IndexTaskProcessor 在索引成功后触发。
     * 此处仅做任务创建与标记，不等待 LLM 响应。
     */
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

    /**
     * 编译单文档为 Wiki 页。
     * LLM 调用（阻塞 I/O）在事务外执行，仅 DB 写入使用短事务。
     */
    public void compileDocument(Long documentId) throws Exception {
        KbDocument doc = documentMapper.selectById(documentId);
        if (doc == null) {
            return;
        }
        WikiCompileTask task = wikiCompileTaskMapper.findByDocumentId(documentId);
        if (task != null) {
            self.updateTaskStatus(task, "COMPILING", null);
        }
        try {
            // LLM 调用在事务外 —— 避免长时间持有 DB 连接
            String text = textExtractionService.extractFullText(doc);
            String sourceHint = buildSourceHint(doc);
            String userPrompt = sourceHint + "\n\n原文摘录：\n" + StringUtils.truncate(text, 12000);
            String contentMd = chatModel.chat(SystemMessage.from(WIKI_SYSTEM), UserMessage.from(userPrompt))
                    .aiMessage()
                    .text();

            // 仅 DB 写入需要事务
            self.saveWikiResult(doc, documentId, contentMd, task);
            auditLogService.log("WIKI_COMPILE", "DOCUMENT", documentId, doc.getTitle());
        } catch (Exception e) {
            if (task != null) {
                self.updateTaskStatus(task, "FAILED", StringUtils.truncate(e.getMessage(), 500));
            }
            throw e;
        }
    }

    /** DB 写入：插入或更新 Wiki 页 + 标记任务完成，在短事务中完成 */
    @Transactional
    protected void saveWikiResult(KbDocument doc, Long documentId, String contentMd,
                                  WikiCompileTask task) {
        String slug = "doc-" + documentId;
        WikiPage page = wikiPageMapper.findByKbAndSlug(doc.getKbId(), slug);
        boolean autoPublish = properties.getWiki().isAutoPublish();
        if (page == null) {
            page = new WikiPage();
            page.setKbId(doc.getKbId());
            page.setSlug(slug);
            page.setTitle(doc.getTitle());
            page.setContentMd(contentMd);
            page.setSourceDocIds(String.valueOf(documentId));
            page.setVersion(1);
            page.setStatus(autoPublish ? "PUBLISHED" : "DRAFT");
            wikiPageMapper.insert(page);
        } else {
            page.setTitle(doc.getTitle());
            page.setContentMd(contentMd);
            page.setSourceDocIds(String.valueOf(documentId));
            page.setVersion(page.getVersion() != null ? page.getVersion() + 1 : 1);
            if (autoPublish) {
                page.setStatus("PUBLISHED");
            }
            wikiPageMapper.updateById(page);
        }
        if (task != null) {
            task.setStatus("DONE");
            task.setErrorMsg(null);
            wikiCompileTaskMapper.updateById(task);
        }
    }

    /** 更新任务状态，独立事务 */
    @Transactional
    protected void updateTaskStatus(WikiCompileTask task, String status, String errorMsg) {
        task.setStatus(status);
        task.setErrorMsg(errorMsg);
        wikiCompileTaskMapper.updateById(task);
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
}
