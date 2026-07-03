package com.fast.knowledge.service;

import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.langchain4j.ingest.KbDocumentSplitter;
import com.fast.knowledge.langchain4j.ingest.KbEmbeddingIngestor;
import com.fast.knowledge.langchain4j.store.KbVectorIndexService;
import com.fast.knowledge.mapper.DocumentChunkMapper;
import com.fast.knowledge.mapper.DocumentMapper;
import com.fast.knowledge.mapper.IndexTaskMapper;
import com.fast.knowledge.model.entity.DocumentChunk;
import com.fast.knowledge.model.entity.IndexTask;
import com.fast.knowledge.model.entity.KbDocument;
import com.fast.knowledge.storage.StorageProvider;
import lombok.extern.slf4j.Slf4j;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.apache.tika.Tika;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
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
    private final KbDocumentSplitter documentSplitter;
    private final KbEmbeddingIngestor embeddingIngestor;
    private final KbVectorIndexService vectorIndexService;
    private final StorageProvider storageProvider;
    private final com.fast.knowledge.cache.CacheProvider cacheProvider;
    private final SearchCacheService searchCacheService;
    private final com.fast.knowledge.langchain4j.assistant.KbChatAssistantFactory kbChatAssistantFactory;
    private final WikiCompileService wikiCompileService;
    private final Tika tika = new Tika();

    public DocumentIngestService(DocumentMapper documentMapper,
                                 DocumentChunkMapper documentChunkMapper,
                                 IndexTaskMapper indexTaskMapper,
                                 ChunkService chunkService,
                                 KbDocumentSplitter documentSplitter,
                                 KbEmbeddingIngestor embeddingIngestor,
                                 KbVectorIndexService vectorIndexService,
                                 StorageProvider storageProvider,
                                 com.fast.knowledge.cache.CacheProvider cacheProvider,
                                 SearchCacheService searchCacheService,
                                 com.fast.knowledge.langchain4j.assistant.KbChatAssistantFactory kbChatAssistantFactory,
                                 WikiCompileService wikiCompileService) {
        this.documentMapper = documentMapper;
        this.documentChunkMapper = documentChunkMapper;
        this.indexTaskMapper = indexTaskMapper;
        this.chunkService = chunkService;
        this.documentSplitter = documentSplitter;
        this.embeddingIngestor = embeddingIngestor;
        this.vectorIndexService = vectorIndexService;
        this.storageProvider = storageProvider;
        this.cacheProvider = cacheProvider;
        this.searchCacheService = searchCacheService;
        this.kbChatAssistantFactory = kbChatAssistantFactory;
        this.wikiCompileService = wikiCompileService;
    }

    @Async("indexExecutor")
    public void scheduleIndex(Long documentId) {
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
            indexDocument(documentId);
        } finally {
            cacheProvider.delete(lockKey);
            indexTaskMapper.releaseLock(documentId, lockOwner);
        }
    }

    private int tryAcquireLock(Long documentId, String lockOwner) {
        LocalDateTime now = LocalDateTime.now();
        return indexTaskMapper.tryAcquireLock(documentId, lockOwner, now, now.minusMinutes(10));
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
            indexTaskMapper.updateById(task);
            scheduleIndex(task.getDocumentId());
        }
    }

    @Transactional
    public void indexDocument(Long documentId) {
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
            String text = extractText(doc);
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
                doc.setChunkCount(saved.size());
            } else {
                doc.setChunkCount(0);
            }
            doc.setIndexStatus("INDEXED");
            doc.setIndexError(null);
            searchCacheService.invalidateForKb(doc.getKbId());
            kbChatAssistantFactory.evict(doc.getKbId());
            wikiCompileService.scheduleCompile(doc.getId());
            if (task != null) {
                task.setStatus("DONE");
                indexTaskMapper.updateById(task);
            }
        } catch (Exception e) {
            log.error("索引文档失败 docId={}", documentId, e);
            doc.setIndexStatus("FAILED");
            doc.setIndexError(e.getMessage());
            if (task != null) {
                task.setStatus("FAILED");
                task.setErrorMsg(e.getMessage());
                task.setRetryCount(task.getRetryCount() + 1);
                indexTaskMapper.updateById(task);
            }
            throw new BusinessException("索引失败: " + e.getMessage());
        } finally {
            documentMapper.updateById(doc);
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
}
