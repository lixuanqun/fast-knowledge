package com.fast.knowledge.service;

import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.langchain4j.ingest.KbDocumentSplitter;
import com.fast.knowledge.langchain4j.ingest.KbEmbeddingIngestor;
import com.fast.knowledge.langchain4j.store.KbVectorIndexService;
import com.fast.knowledge.mapper.DocumentChunkMapper;
import com.fast.knowledge.mapper.DocumentMapper;
import com.fast.knowledge.mapper.IndexTaskMapper;
import com.fast.knowledge.model.entity.DocumentChunk;
import com.fast.knowledge.model.entity.IndexTask;
import com.fast.knowledge.model.entity.KbDocument;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final IndexTaskMapper indexTaskMapper;
    private final ChunkService chunkService;
    private final KbDocumentSplitter documentSplitter;
    private final KbEmbeddingIngestor embeddingIngestor;
    private final KbVectorIndexService vectorIndexService;
    private final com.fast.knowledge.cache.CacheProvider cacheProvider;
    private final SearchCacheService searchCacheService;
    private final com.fast.knowledge.langchain4j.assistant.KbChatAssistantFactory kbChatAssistantFactory;
    private final WikiCompileService wikiCompileService;
    private final TextExtractionService textExtractionService;
    private final MetricsService metricsService;
    private final int maxRetry;

    public IndexTaskProcessor(DocumentMapper documentMapper,
                               DocumentChunkMapper documentChunkMapper,
                               IndexTaskMapper indexTaskMapper,
                               ChunkService chunkService,
                               KbDocumentSplitter documentSplitter,
                               KbEmbeddingIngestor embeddingIngestor,
                               KbVectorIndexService vectorIndexService,
                               com.fast.knowledge.cache.CacheProvider cacheProvider,
                               SearchCacheService searchCacheService,
                               com.fast.knowledge.langchain4j.assistant.KbChatAssistantFactory kbChatAssistantFactory,
                               WikiCompileService wikiCompileService,
                               TextExtractionService textExtractionService,
                               MetricsService metricsService,
                               KnowledgeProperties properties) {
        this.documentMapper = documentMapper;
        this.documentChunkMapper = documentChunkMapper;
        this.indexTaskMapper = indexTaskMapper;
        this.chunkService = chunkService;
        this.documentSplitter = documentSplitter;
        this.embeddingIngestor = embeddingIngestor;
        this.vectorIndexService = vectorIndexService;
        this.cacheProvider = cacheProvider;
        this.searchCacheService = searchCacheService;
        this.kbChatAssistantFactory = kbChatAssistantFactory;
        this.wikiCompileService = wikiCompileService;
        this.textExtractionService = textExtractionService;
        this.metricsService = metricsService;
        this.maxRetry = Math.max(1, properties.getIndex().getMaxRetry());
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
                    Metadata docMetadata = Metadata.from(Map.of(
                            "kbId", doc.getKbId(),
                            "docId", documentId,
                            "title", doc.getTitle() != null ? doc.getTitle() : ""
                    ));
                    List<TextSegment> splitSegments = documentSplitter.split(Document.from(text, docMetadata));
                    documentChunkMapper.deleteByDocumentId(documentId);
                    vectorIndexService.deleteByDocument(doc.getKbId(), documentId);

                    List<DocumentChunk> chunks = new ArrayList<>();
                    for (int i = 0; i < splitSegments.size(); i++) {
                        String content = splitSegments.get(i).text();
                        DocumentChunk chunk = new DocumentChunk();
                        chunk.setKbId(doc.getKbId());
                        chunk.setDocumentId(documentId);
                        chunk.setChunkIndex(i);
                        chunk.setContent(content);
                        chunk.setSectionTitle(chunkService.extractSectionTitle(content));
                        chunk.setTokenCount(chunkService.countTokens(content));
                        chunks.add(chunk);
                    }
                    if (!chunks.isEmpty()) {
                        documentChunkMapper.batchInsert(chunks);
                        List<DocumentChunk> saved = documentChunkMapper.findByDocumentId(documentId);
                        embeddingIngestor.embedChunks(doc, saved);
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
                kbChatAssistantFactory.evict(doc.getKbId());
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
}
