package com.fast.knowledge.service;

import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.embedding.EmbeddingProvider;
import com.fast.knowledge.langchain4j.lucene.LuceneEmbeddingStore;
import com.fast.knowledge.langchain4j.lucene.LuceneEmbeddingStoreFactory;
import com.fast.knowledge.mapper.DocumentChunkMapper;
import com.fast.knowledge.mapper.DocumentMapper;
import com.fast.knowledge.mapper.IndexTaskMapper;
import com.fast.knowledge.model.entity.DocumentChunk;
import com.fast.knowledge.model.entity.IndexTask;
import com.fast.knowledge.model.entity.KbDocument;
import com.fast.knowledge.vector.VectorStore;
import lombok.extern.slf4j.Slf4j;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.apache.tika.Tika;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class DocumentIngestService {

    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final IndexTaskMapper indexTaskMapper;
    private final ChunkService chunkService;
    private final EmbeddingProvider embeddingProvider;
    private final LuceneEmbeddingStoreFactory luceneEmbeddingStoreFactory;
    private final VectorStore vectorStore;
    private final com.fast.knowledge.cache.CacheProvider cacheProvider;
    private final Tika tika = new Tika();

    public DocumentIngestService(DocumentMapper documentMapper,
                                 DocumentChunkMapper documentChunkMapper,
                                 IndexTaskMapper indexTaskMapper,
                                 ChunkService chunkService,
                                 EmbeddingProvider embeddingProvider,
                                 LuceneEmbeddingStoreFactory luceneEmbeddingStoreFactory,
                                 VectorStore vectorStore,
                                 com.fast.knowledge.cache.CacheProvider cacheProvider) {
        this.documentMapper = documentMapper;
        this.documentChunkMapper = documentChunkMapper;
        this.indexTaskMapper = indexTaskMapper;
        this.chunkService = chunkService;
        this.embeddingProvider = embeddingProvider;
        this.luceneEmbeddingStoreFactory = luceneEmbeddingStoreFactory;
        this.vectorStore = vectorStore;
        this.cacheProvider = cacheProvider;
    }

    @Async("indexExecutor")
    public void scheduleIndex(Long documentId) {
        String lockOwner = UUID.randomUUID().toString();
        String lockKey = "kb:index:lock:" + documentId;
        boolean locked = cacheProvider.setIfAbsent(lockKey, lockOwner, java.time.Duration.ofMinutes(10));
        if (!locked) {
            IndexTask task = indexTaskMapper.findByDocumentId(documentId);
            if (task != null && indexTaskMapper.tryAcquireLock(documentId, lockOwner) == 0) {
                return;
            }
        } else if (indexTaskMapper.findByDocumentId(documentId) != null) {
            indexTaskMapper.tryAcquireLock(documentId, lockOwner);
        }
        try {
            indexDocument(documentId);
        } finally {
            cacheProvider.delete(lockKey);
            indexTaskMapper.releaseLock(documentId, lockOwner);
        }
    }

    @Scheduled(fixedDelay = 30000)
    public void pollPendingTasks() {
        List<IndexTask> tasks = indexTaskMapper.findPending(5);
        for (IndexTask task : tasks) {
            scheduleIndex(task.getDocumentId());
        }
        List<IndexTask> retryable = indexTaskMapper.findRetryable(3, 3);
        for (IndexTask task : retryable) {
            task.setStatus("PENDING");
            indexTaskMapper.update(task);
            scheduleIndex(task.getDocumentId());
        }
    }

    @Transactional
    public void indexDocument(Long documentId) {
        KbDocument doc = documentMapper.findById(documentId);
        if (doc == null) {
            return;
        }
        IndexTask task = indexTaskMapper.findByDocumentId(documentId);
        if (task != null) {
            task.setStatus("INDEXING");
            indexTaskMapper.update(task);
        }
        doc.setIndexStatus("INDEXING");
        documentMapper.update(doc);
        try {
            String text = extractText(doc);
            List<String> parts = chunkService.split(text);
            documentChunkMapper.deleteByDocumentId(documentId);
            vectorStore.deleteByDocument(doc.getKbId(), documentId);

            List<DocumentChunk> chunks = new ArrayList<>();
            for (int i = 0; i < parts.size(); i++) {
                DocumentChunk chunk = new DocumentChunk();
                chunk.setKbId(doc.getKbId());
                chunk.setDocumentId(documentId);
                chunk.setChunkIndex(i);
                chunk.setContent(parts.get(i));
                chunk.setTokenCount(chunkService.countTokens(parts.get(i)));
                chunks.add(chunk);
            }
            if (!chunks.isEmpty()) {
                documentChunkMapper.batchInsert(chunks);
                List<DocumentChunk> saved = documentChunkMapper.findByDocumentId(documentId);
                List<String> texts = saved.stream().map(DocumentChunk::getContent).toList();
                List<float[]> vectors = embeddingProvider.embedBatch(texts);
                LuceneEmbeddingStore embeddingStore = luceneEmbeddingStoreFactory.getStore(doc.getKbId());
                List<Embedding> embeddings = new ArrayList<>(saved.size());
                List<TextSegment> segments = new ArrayList<>(saved.size());
                for (int i = 0; i < saved.size(); i++) {
                    DocumentChunk chunk = saved.get(i);
                    Metadata metadata = Metadata.from(Map.of(
                            LuceneEmbeddingStore.META_DOC_ID, documentId,
                            LuceneEmbeddingStore.META_CHUNK_ID, chunk.getId(),
                            LuceneEmbeddingStore.META_TITLE, doc.getTitle()
                    ));
                    segments.add(TextSegment.from(chunk.getContent(), metadata));
                    embeddings.add(Embedding.from(vectors.get(i)));
                }
                embeddingStore.addAll(embeddings, segments);
                doc.setChunkCount(saved.size());
            } else {
                doc.setChunkCount(0);
            }
            doc.setIndexStatus("INDEXED");
            doc.setIndexError(null);
            if (task != null) {
                task.setStatus("DONE");
                indexTaskMapper.update(task);
            }
        } catch (Exception e) {
            log.error("索引文档失败 docId={}", documentId, e);
            doc.setIndexStatus("FAILED");
            doc.setIndexError(e.getMessage());
            if (task != null) {
                task.setStatus("FAILED");
                task.setErrorMsg(e.getMessage());
                task.setRetryCount(task.getRetryCount() + 1);
                indexTaskMapper.update(task);
            }
            throw new BusinessException("索引失败: " + e.getMessage());
        } finally {
            documentMapper.update(doc);
        }
    }

    private String extractText(KbDocument doc) throws Exception {
        String path = doc.getFilePath();
        if ("txt".equalsIgnoreCase(doc.getFileType()) || "md".equalsIgnoreCase(doc.getFileType())) {
            return Files.readString(Paths.get(path));
        }
        return tika.parseToString(Paths.get(path).toFile());
    }
}
