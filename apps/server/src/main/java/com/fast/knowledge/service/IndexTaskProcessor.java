package com.fast.knowledge.service;

import com.fast.knowledge.ai.port.ChunkContextPort;
import com.fast.knowledge.ai.port.ConversationPort;
import com.fast.knowledge.ai.port.IngestPort;
import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.mapper.DocumentChunkMapper;
import com.fast.knowledge.mapper.DocumentMapper;
import com.fast.knowledge.mapper.IndexTaskMapper;
import com.fast.knowledge.model.entity.DocumentChunk;
import com.fast.knowledge.model.entity.IndexTask;
import com.fast.knowledge.model.entity.KbDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 索引任务异步处理器 — 独立 Bean 确保 {@code @Async} 和 {@code @Transactional} AOP 代理生效。
 *
 * <p>为什么独立：{@code DocumentIngestServiceImpl} 内部 self-invocation 会绕过 Spring AOP，
 * 导致 {@code @Async} 和 {@code @Transactional} 完全失效。将此逻辑移到独立 Bean
 * 中可以安全调用并保证事务边界和异步执行。
 */
@Slf4j
@Component
public class IndexTaskProcessor {

    /** OCR 全文页标记（OcrParseService 拼接格式） */
    private static final Pattern PAGE_MARK_PATTERN = Pattern.compile("<!--\\s*page\\s+(\\d+)\\s*-->");
    /** Markdown 表格：表头行后跟分隔行（| --- | --- |） */
    private static final Pattern MD_TABLE_PATTERN =
            Pattern.compile("(?m)^\\s*\\|.*\\|\\s*\\r?\\n\\s*\\|[ :\\-|]+\\|\\s*$");

    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final IndexTaskMapper indexTaskMapper;
    private final ChunkService chunkService;
    private final ChunkContextPort chunkContextPort;
    private final OcrParseService ocrParseService;
    private final IngestPort ingestPort;
    private final ConversationPort conversationPort;
    private final com.fast.knowledge.cache.CacheProvider cacheProvider;
    private final SearchCacheService searchCacheService;
    private final WikiCompileService wikiCompileService;
    private final TextExtractionService textExtractionService;
    private final MetricsService metricsService;
    private final KnowledgeProperties properties;
    private final int maxRetry;
    private final int contextBatchSize;
    private final int contextDocPreviewChars;

    public IndexTaskProcessor(DocumentMapper documentMapper,
                               DocumentChunkMapper documentChunkMapper,
                               IndexTaskMapper indexTaskMapper,
                               ChunkService chunkService,
                               ChunkContextPort chunkContextPort,
                               OcrParseService ocrParseService,
                               IngestPort ingestPort,
                               ConversationPort conversationPort,
                               com.fast.knowledge.cache.CacheProvider cacheProvider,
                               SearchCacheService searchCacheService,
                               WikiCompileService wikiCompileService,
                               TextExtractionService textExtractionService,
                               MetricsService metricsService,
                               KnowledgeProperties properties) {
        this.documentMapper = documentMapper;
        this.documentChunkMapper = documentChunkMapper;
        this.indexTaskMapper = indexTaskMapper;
        this.chunkService = chunkService;
        this.chunkContextPort = chunkContextPort;
        this.ocrParseService = ocrParseService;
        this.ingestPort = ingestPort;
        this.conversationPort = conversationPort;
        this.cacheProvider = cacheProvider;
        this.searchCacheService = searchCacheService;
        this.wikiCompileService = wikiCompileService;
        this.textExtractionService = textExtractionService;
        this.metricsService = metricsService;
        this.properties = properties;
        this.maxRetry = Math.max(1, properties.getIndex().getMaxRetry());
        this.contextBatchSize = properties.getIngest().getContextBatchSize();
        this.contextDocPreviewChars = properties.getIngest().getContextDocPreviewChars();
    }

    /**
     * 异步调度索引（含分布式锁），由 {@code DocumentIngestServiceImpl.scheduleIndex} 委托。
     */
    @Async("indexExecutor")
    public void schedule(Long documentId) {
        String lockOwner = UUID.randomUUID().toString();
        String lockKey = "kb:index:lock:" + documentId;
        boolean locked = cacheProvider.setIfAbsent(lockKey, lockOwner, java.time.Duration.ofMinutes(10));
        if (!locked) {
            IndexTask task = indexTaskMapper.findByDocumentId(documentId);
            if (task != null && tryAcquireLock(documentId, lockOwner) == 0) {
                return;
            }
        } else if (indexTaskMapper.findByDocumentId(documentId) != null) {
            tryAcquireLock(documentId, lockOwner);
        }
        try {
            execute(documentId);
        } finally {
            cacheProvider.delete(lockKey);
            indexTaskMapper.releaseLock(documentId, lockOwner);
        }
    }

    private int tryAcquireLock(Long documentId, String lockOwner) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        return indexTaskMapper.tryAcquireLock(documentId, lockOwner, now, now.minusMinutes(10));
    }

    /**
     * 执行完整索引流水线（有事务边界）。
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public void execute(Long documentId) {
        KbDocument doc = documentMapper.selectById(documentId);
        if (doc == null) {
            return;
        }
        IndexTask task = indexTaskMapper.findByDocumentId(documentId);
        if (task != null) {
            task.setStatus("INDEXING");
            indexTaskMapper.updateById(task);
        }
        doc.setIndexStatus("INDEXING");
        documentMapper.updateById(doc);

        try {
            int chunkCount = metricsService.timeIndex(() -> {
                try {
                    String text = textExtractionService.extractFullText(doc);
                    // WP2 扫描件 OCR：无文本层 PDF / 图片文档用视觉模型转 Markdown 后再走常规分块
                    if (ocrParseService.isEnabled() && ocrParseService.isScanCandidate(doc, text)) {
                        log.info("docId={} 检测为扫描件/图片，启动 OCR 解析", documentId);
                        text = ocrParseService.parseToMarkdown(doc);
                    }
                    List<String> splitSegments = ingestPort.split(text, doc.getKbId(), documentId, doc.getTitle());
                    documentChunkMapper.deleteByDocumentId(documentId);
                    ingestPort.deleteByDocument(doc.getKbId(), documentId);

                    // WP1 上下文化分块：按批为分块生成上下文前缀（失败降级为空串）
                    List<String> contextPrefixes = generateContextPrefixes(doc.getTitle(), text, splitSegments);

                    // WP5 溯源：页码标记预扫描（OCR 文档含 <!-- page N -->），逐块推算页码与锚点类型
                    List<Integer> pageMarks = scanPageMarks(text);
                    int pageCursor = 0;

                    List<DocumentChunk> chunks = new ArrayList<>();
                    for (int i = 0; i < splitSegments.size(); i++) {
                        String content = splitSegments.get(i);
                        DocumentChunk chunk = new DocumentChunk();
                        chunk.setKbId(doc.getKbId());
                        chunk.setDocumentId(documentId);
                        chunk.setChunkIndex(i);
                        chunk.setContent(content);
                        chunk.setSectionTitle(chunkService.extractSectionTitle(content));
                        chunk.setContextPrefix(contextPrefixes.get(i));
                        pageCursor = resolvePageNo(content, pageMarks, pageCursor);
                        chunk.setPageNo(pageCursor > 0 ? pageCursor : null);
                        chunk.setAnchorType(hasMarkdownTable(content) ? "table" : "text");
                        chunk.setTokenCount(chunkService.countTokens(content));
                        chunks.add(chunk);
                    }
                    if (!chunks.isEmpty()) {
                        documentChunkMapper.batchInsert(chunks);
                        List<DocumentChunk> saved = documentChunkMapper.findByDocumentId(documentId);
                        ingestPort.embedChunks(doc, saved);
                        return saved.size();
                    }
                    return 0;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            doc.setChunkCount(chunkCount);
            doc.setIndexStatus("INDEXED");
            doc.setIndexError(null);
            metricsService.countIndex(chunkCount);

            if (task != null) {
                task.setStatus("DONE");
                indexTaskMapper.updateById(task);
            }

            // Best-effort post-indexing operations: failures here don't revert index status
            try {
                searchCacheService.invalidateForKb(doc.getKbId());
                conversationPort.evictAssistant(doc.getKbId());
                wikiCompileService.scheduleCompile(doc.getId());
            } catch (Exception postEx) {
                log.warn("Post-indexing cleanup failed docId={}: {}", documentId, postEx.getMessage());
            }
        } catch (Exception e) {
            log.error("索引文档失败 docId={}", documentId, e);
            doc.setIndexStatus("FAILED");
            doc.setIndexError(e.getMessage());
            if (task != null) {
                int retryCount = task.getRetryCount() != null ? task.getRetryCount() : 0;
                if (retryCount >= maxRetry) {
                    task.setStatus("DEAD");
                    task.setErrorMsg(e.getMessage() + " (retries exhausted: " + retryCount + ")");
                } else {
                    task.setStatus("FAILED");
                    task.setErrorMsg(e.getMessage());
                    task.setRetryCount(retryCount + 1);
                }
                indexTaskMapper.updateById(task);
            }
            throw new BusinessException("索引失败: " + e.getMessage());
        } finally {
            documentMapper.updateById(doc);
        }
    }

    /**
     * WP1 上下文化分块：按批调用 LLM 为每个分块生成上下文前缀。
     * 开关关闭或分块为空时返回空串占位；LLM 异常按批降级，不阻塞索引。
     */
    private List<String> generateContextPrefixes(String docTitle, String fullText, List<String> segments) {
        List<String> prefixes = new ArrayList<>(segments.size());
        for (int i = 0; i < segments.size(); i++) {
            prefixes.add("");
        }
        if (!properties.getIngest().isContextualEnabled()) {
            return prefixes;
        }
        int batchSize = Math.max(1, contextBatchSize);
        String preview = fullText == null ? "" : fullText.substring(0, Math.min(fullText.length(), contextDocPreviewChars));
        for (int from = 0; from < segments.size(); from += batchSize) {
            int to = Math.min(from + batchSize, segments.size());
            try {
                List<String> batch = chunkContextPort.generateContexts(docTitle, preview, segments.subList(from, to));
                for (int i = from; i < to && i - from < batch.size(); i++) {
                    prefixes.set(i, batch.get(i - from));
                }
                log.info("上下文前缀生成 批次 [{},{}) 完成 {}/{}", from, to, batch.size(), to - from);
            } catch (Exception e) {
                log.warn("上下文前缀生成 批次 [{},{}) 失败，降级为无前缀: {}", from, to, e.getMessage());
            }
        }
        return prefixes;
    }

    /** OCR 全文中的页标记（<!-- page N -->）出现位置 → 页码序列（按文本顺序） */
    private List<Integer> scanPageMarks(String text) {
        List<Integer> marks = new ArrayList<>();
        if (text == null) {
            return marks;
        }
        Matcher m = PAGE_MARK_PATTERN.matcher(text);
        while (m.find()) {
            marks.add(Integer.parseInt(m.group(1)));
        }
        return marks;
    }

    /**
     * 推算分块所在页码：分块内容携带页标记（OCR 拼接文本按页分块，起始标记可靠）取标记页码；
     * 不携带标记的延续块沿用前一分块页码。非分页文档返回 0（调用方落 null）。
     */
    private int resolvePageNo(String content, List<Integer> pageMarks, int prevPage) {
        if (pageMarks.isEmpty()) {
            return 0;
        }
        Matcher m = PAGE_MARK_PATTERN.matcher(content == null ? "" : content);
        return m.find() ? Integer.parseInt(m.group(1)) : prevPage;
    }

    /** Markdown 表格检测：表头行 + 分隔行（| --- |） */
    private boolean hasMarkdownTable(String content) {
        if (content == null) {
            return false;
        }
        return MD_TABLE_PATTERN.matcher(content).find();
    }
}
