package com.fast.knowledge.langchain4j.adapter;

import com.fast.knowledge.ai.port.IngestPort;
import com.fast.knowledge.langchain4j.KbEmbeddingStoreFactory;
import com.fast.knowledge.langchain4j.assistant.KbAssistantFactory;
import com.fast.knowledge.langchain4j.ingest.KbDocumentSplitter;
import com.fast.knowledge.langchain4j.ingest.KbEmbeddingIngestor;
import com.fast.knowledge.langchain4j.retrieval.KbContentRetrieverFactory;
import com.fast.knowledge.langchain4j.store.KbVectorIndexService;
import com.fast.knowledge.model.entity.DocumentChunk;
import com.fast.knowledge.model.entity.KbDocument;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class LangChain4jIngestAdapter implements IngestPort {

    private final KbDocumentSplitter documentSplitter;
    private final KbEmbeddingIngestor embeddingIngestor;
    private final KbVectorIndexService vectorIndexService;
    private final KbEmbeddingStoreFactory embeddingStoreFactory;
    private final KbContentRetrieverFactory contentRetrieverFactory;
    private final KbAssistantFactory kbAssistantFactory;

    public LangChain4jIngestAdapter(KbDocumentSplitter documentSplitter,
                                    KbEmbeddingIngestor embeddingIngestor,
                                    KbVectorIndexService vectorIndexService,
                                    KbEmbeddingStoreFactory embeddingStoreFactory,
                                    @Lazy KbContentRetrieverFactory contentRetrieverFactory,
                                    @Lazy KbAssistantFactory kbAssistantFactory) {
        this.documentSplitter = documentSplitter;
        this.embeddingIngestor = embeddingIngestor;
        this.vectorIndexService = vectorIndexService;
        this.embeddingStoreFactory = embeddingStoreFactory;
        this.contentRetrieverFactory = contentRetrieverFactory;
        this.kbAssistantFactory = kbAssistantFactory;
    }

    @Override
    public List<String> split(String fullText, Long kbId, Long docId, String title) {
        Metadata docMetadata = Metadata.from(Map.of(
                "kbId", kbId,
                "docId", docId,
                "title", title != null ? title : ""
        ));
        return documentSplitter.split(Document.from(fullText, docMetadata)).stream()
                .map(TextSegment::text)
                .toList();
    }

    @Override
    public void embedChunks(KbDocument doc, List<DocumentChunk> chunks) {
        embeddingIngestor.embedChunks(doc, chunks);
    }

    @Override
    public void deleteByDocument(Long kbId, Long docId) {
        vectorIndexService.deleteByDocument(kbId, docId);
    }

    @Override
    public void deleteChunk(Long kbId, Long chunkId) {
        vectorIndexService.deleteChunk(kbId, chunkId);
    }

    @Override
    public void deleteKb(Long kbId) {
        vectorIndexService.deleteKb(kbId);
    }

    @Override
    public void evictKbRuntime(Long kbId) {
        embeddingStoreFactory.evict(kbId);
        contentRetrieverFactory.evict(kbId);
        kbAssistantFactory.evict(kbId);
    }
}
