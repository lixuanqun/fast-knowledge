package com.fast.knowledge.langchain4j.ingest;

import com.fast.knowledge.langchain4j.KbEmbeddingStore;
import com.fast.knowledge.langchain4j.KbEmbeddingStoreFactory;
import com.fast.knowledge.model.entity.DocumentChunk;
import com.fast.knowledge.model.entity.KbDocument;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * LangChain4j 文档向量化摄入：分块元数据绑定后批量嵌入并写入 PgVector。
 */
@Service
public class KbEmbeddingIngestor {

    private final EmbeddingModel embeddingModel;
    private final KbEmbeddingStoreFactory embeddingStoreFactory;

    public KbEmbeddingIngestor(EmbeddingModel embeddingModel,
                               KbEmbeddingStoreFactory embeddingStoreFactory) {
        this.embeddingModel = embeddingModel;
        this.embeddingStoreFactory = embeddingStoreFactory;
    }

    /**
     * 为已持久化的分块生成向量并写入 EmbeddingStore。
     */
    public void embedChunks(KbDocument doc, List<DocumentChunk> chunks) {
        if (chunks.isEmpty()) {
            return;
        }
        KbEmbeddingStore store = embeddingStoreFactory.getStore(doc.getKbId());
        List<String> ids = new ArrayList<>(chunks.size());
        List<TextSegment> segments = new ArrayList<>(chunks.size());
        for (DocumentChunk chunk : chunks) {
            String section = chunk.getSectionTitle() != null
                    ? chunk.getSectionTitle()
                    : "";
            Metadata metadata = Metadata.from(Map.of(
                    KbEmbeddingStore.META_KB_ID, doc.getKbId(),
                    KbEmbeddingStore.META_DOC_ID, doc.getId(),
                    KbEmbeddingStore.META_CHUNK_ID, chunk.getId(),
                    KbEmbeddingStore.META_TITLE, doc.getTitle() != null ? doc.getTitle() : "",
                    KbEmbeddingStore.META_DOC_TYPE, doc.getDocType() != null ? doc.getDocType() : "",
                    KbEmbeddingStore.META_DOC_NO, doc.getDocNo() != null ? doc.getDocNo() : "",
                    KbEmbeddingStore.META_SECTION, section
            ));
            ids.add(UUID.randomUUID().toString());
            segments.add(TextSegment.from(chunk.getContent(), metadata));
        }
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        store.addAll(ids, embeddings, segments);
    }

    /**
     * 构建按知识库隔离的 EmbeddingStoreIngestor（用于整文档摄入场景）。
     */
    public EmbeddingStoreIngestor ingestorFor(Long kbId, KbDocumentSplitter splitter) {
        return EmbeddingStoreIngestor.builder()
                .documentSplitter(splitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStoreFactory.getStore(kbId))
                .build();
    }
}
