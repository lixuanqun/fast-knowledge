package com.fast.knowledge.service;

import com.fast.knowledge.langchain4j.assistant.KbAssistantFactory;
import com.fast.knowledge.langchain4j.assistant.KbChatAssistantFactory;
import com.fast.knowledge.langchain4j.ingest.KbEmbeddingIngestor;
import com.fast.knowledge.langchain4j.retrieval.KbContentRetrieverFactory;
import com.fast.knowledge.langchain4j.KbEmbeddingStoreFactory;
import com.fast.knowledge.langchain4j.store.KbVectorIndexService;
import com.fast.knowledge.mapper.DocumentChunkMapper;
import com.fast.knowledge.mapper.DocumentMapper;
import com.fast.knowledge.model.entity.DocumentChunk;
import com.fast.knowledge.model.entity.KbDocument;
import com.fast.knowledge.model.entity.KnowledgeBase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class IndexRebuildService {

    private final DocumentChunkMapper documentChunkMapper;
    private final DocumentMapper documentMapper;
    private final KbEmbeddingStoreFactory kbEmbeddingStoreFactory;
    private final KbContentRetrieverFactory kbContentRetrieverFactory;
    private final KbAssistantFactory kbAssistantFactory;
    private final KbChatAssistantFactory kbChatAssistantFactory;
    private final KbVectorIndexService vectorIndexService;
    private final KbEmbeddingIngestor embeddingIngestor;
    private final KnowledgeBaseService knowledgeBaseService;
    private final AuditLogService auditLogService;
    private final SearchCacheService searchCacheService;

    public IndexRebuildService(DocumentChunkMapper documentChunkMapper,
                               DocumentMapper documentMapper,
                               KbEmbeddingStoreFactory kbEmbeddingStoreFactory,
                               KbContentRetrieverFactory kbContentRetrieverFactory,
                               KbAssistantFactory kbAssistantFactory,
                               KbChatAssistantFactory kbChatAssistantFactory,
                               KbVectorIndexService vectorIndexService,
                               KbEmbeddingIngestor embeddingIngestor,
                               KnowledgeBaseService knowledgeBaseService,
                               AuditLogService auditLogService,
                               SearchCacheService searchCacheService) {
        this.documentChunkMapper = documentChunkMapper;
        this.documentMapper = documentMapper;
        this.kbEmbeddingStoreFactory = kbEmbeddingStoreFactory;
        this.kbContentRetrieverFactory = kbContentRetrieverFactory;
        this.kbAssistantFactory = kbAssistantFactory;
        this.kbChatAssistantFactory = kbChatAssistantFactory;
        this.vectorIndexService = vectorIndexService;
        this.embeddingIngestor = embeddingIngestor;
        this.knowledgeBaseService = knowledgeBaseService;
        this.auditLogService = auditLogService;
        this.searchCacheService = searchCacheService;
    }

    public void requestRebuild(Long kbId) {
        KnowledgeBase kb = knowledgeBaseService.getById(kbId);
        knowledgeBaseService.checkWritePermission(kb);
        rebuildKbIndexAsync(kbId);
    }

    @Async("indexExecutor")
    public void rebuildKbIndexAsync(Long kbId) {
        try {
            int count = rebuildKbIndex(kbId);
            log.info("知识库 {} 后台索引重建完成，共 {} 个分块", kbId, count);
        } catch (Exception e) {
            log.error("知识库 {} 索引重建失败", kbId, e);
        }
    }

    public int rebuildKbIndex(Long kbId) throws Exception {
        KnowledgeBase kb = knowledgeBaseService.getById(kbId);
        knowledgeBaseService.checkWritePermission(kb);
        vectorIndexService.deleteKb(kbId);
        kbEmbeddingStoreFactory.evict(kbId);
        kbContentRetrieverFactory.evict(kbId);
        kbAssistantFactory.evict(kbId);
        kbChatAssistantFactory.evict(kbId);

        List<DocumentChunk> chunks = documentChunkMapper.findByKbId(kbId);
        if (chunks.isEmpty()) {
            auditLogService.log("REBUILD_INDEX", "KB", kbId, "chunks=0");
            searchCacheService.invalidateForKb(kbId);
            return 0;
        }

        Map<Long, KbDocument> docCache = new HashMap<>();
        Map<Long, List<DocumentChunk>> byDoc = new HashMap<>();
        for (DocumentChunk chunk : chunks) {
            byDoc.computeIfAbsent(chunk.getDocumentId(), id -> new java.util.ArrayList<>()).add(chunk);
        }
        for (Map.Entry<Long, List<DocumentChunk>> entry : byDoc.entrySet()) {
            KbDocument doc = docCache.computeIfAbsent(entry.getKey(), documentMapper::selectById);
            if (doc != null) {
                embeddingIngestor.embedChunks(doc, entry.getValue());
            }
        }

        auditLogService.log("REBUILD_INDEX", "KB", kbId, "chunks=" + chunks.size());
        searchCacheService.invalidateForKb(kbId);
        log.info("知识库 {} 索引重建完成，共 {} 个分块", kbId, chunks.size());
        return chunks.size();
    }
}
