package com.fast.knowledge.service;

import com.fast.knowledge.embedding.EmbeddingProvider;
import com.fast.knowledge.langchain4j.lucene.KbContentRetrieverFactory;
import com.fast.knowledge.langchain4j.lucene.LuceneEmbeddingStore;
import com.fast.knowledge.langchain4j.lucene.LuceneEmbeddingStoreFactory;
import com.fast.knowledge.mapper.DocumentChunkMapper;
import com.fast.knowledge.mapper.DocumentMapper;
import com.fast.knowledge.model.entity.DocumentChunk;
import com.fast.knowledge.model.entity.KbDocument;
import com.fast.knowledge.model.entity.KnowledgeBase;
import com.fast.knowledge.vector.VectorStore;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class IndexRebuildService {

    private final DocumentChunkMapper documentChunkMapper;
    private final DocumentMapper documentMapper;
    private final EmbeddingProvider embeddingProvider;
    private final LuceneEmbeddingStoreFactory luceneEmbeddingStoreFactory;
    private final KbContentRetrieverFactory kbContentRetrieverFactory;
    private final VectorStore vectorStore;
    private final KnowledgeBaseService knowledgeBaseService;
    private final AuditLogService auditLogService;

    public IndexRebuildService(DocumentChunkMapper documentChunkMapper,
                               DocumentMapper documentMapper,
                               EmbeddingProvider embeddingProvider,
                               LuceneEmbeddingStoreFactory luceneEmbeddingStoreFactory,
                               KbContentRetrieverFactory kbContentRetrieverFactory,
                               VectorStore vectorStore,
                               KnowledgeBaseService knowledgeBaseService,
                               AuditLogService auditLogService) {
        this.documentChunkMapper = documentChunkMapper;
        this.documentMapper = documentMapper;
        this.embeddingProvider = embeddingProvider;
        this.luceneEmbeddingStoreFactory = luceneEmbeddingStoreFactory;
        this.kbContentRetrieverFactory = kbContentRetrieverFactory;
        this.vectorStore = vectorStore;
        this.knowledgeBaseService = knowledgeBaseService;
        this.auditLogService = auditLogService;
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
        vectorStore.deleteKb(kbId);
        luceneEmbeddingStoreFactory.evict(kbId);
        kbContentRetrieverFactory.evict(kbId);

        List<DocumentChunk> chunks = documentChunkMapper.findByKbId(kbId);
        if (chunks.isEmpty()) {
            auditLogService.log("REBUILD_INDEX", "KB", kbId, "chunks=0");
            return 0;
        }

        Map<Long, String> docTitles = new HashMap<>();
        List<String> texts = new ArrayList<>(chunks.size());
        for (DocumentChunk chunk : chunks) {
            texts.add(chunk.getContent());
        }
        List<float[]> vectors = embeddingProvider.embedBatch(texts);

        LuceneEmbeddingStore embeddingStore = luceneEmbeddingStoreFactory.getStore(kbId);
        List<Embedding> embeddings = new ArrayList<>(chunks.size());
        List<TextSegment> segments = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = chunks.get(i);
            String title = docTitles.computeIfAbsent(chunk.getDocumentId(), docId -> {
                KbDocument doc = documentMapper.findById(docId);
                return doc != null ? doc.getTitle() : "文档";
            });
            Metadata metadata = Metadata.from(Map.of(
                    LuceneEmbeddingStore.META_DOC_ID, chunk.getDocumentId(),
                    LuceneEmbeddingStore.META_CHUNK_ID, chunk.getId(),
                    LuceneEmbeddingStore.META_TITLE, title
            ));
            segments.add(TextSegment.from(chunk.getContent(), metadata));
            embeddings.add(Embedding.from(vectors.get(i)));
        }
        embeddingStore.addAll(embeddings, segments);

        auditLogService.log("REBUILD_INDEX", "KB", kbId, "chunks=" + chunks.size());
        log.info("知识库 {} 索引重建完成，共 {} 个分块", kbId, chunks.size());
        return chunks.size();
    }
}
