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
 * LangChain4j 文档向量化摄入：分块元数据绑定后批量嵌入并写入向量存储。
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
        List<TextSegment> embedTargets = new ArrayList<>(chunks.size());
        for (DocumentChunk chunk : chunks) {
            String section = chunk.getSectionTitle() != null
                    ? chunk.getSectionTitle()
                    : "";
            // WP5 溯源元数据：页码与锚点类型可能为 null，Map.of 不允许 null 值，用条件 put
            java.util.Map<String, Object> metaMap = new java.util.LinkedHashMap<>();
            metaMap.put(KbEmbeddingStore.META_KB_ID, doc.getKbId());
            metaMap.put(KbEmbeddingStore.META_DOC_ID, doc.getId());
            metaMap.put(KbEmbeddingStore.META_CHUNK_ID, chunk.getId());
            metaMap.put(KbEmbeddingStore.META_TITLE, doc.getTitle() != null ? doc.getTitle() : "");
            metaMap.put(KbEmbeddingStore.META_DOC_TYPE, doc.getDocType() != null ? doc.getDocType() : "");
            metaMap.put(KbEmbeddingStore.META_DOC_NO, doc.getDocNo() != null ? doc.getDocNo() : "");
            metaMap.put(KbEmbeddingStore.META_SECTION, section);
            if (chunk.getPageNo() != null) {
                metaMap.put(KbEmbeddingStore.META_PAGE_NO, chunk.getPageNo());
            }
            if (chunk.getAnchorType() != null) {
                metaMap.put(KbEmbeddingStore.META_ANCHOR_TYPE, chunk.getAnchorType());
            }
            Metadata metadata = Metadata.from(metaMap);
            ids.add(UUID.randomUUID().toString());
            segments.add(TextSegment.from(chunk.getContent(), metadata));
            // WP1 上下文化分块：向量用"前缀 + 正文"计算，存储段保留原文用于展示与引用
            String embedText = chunk.getContextPrefix() != null && !chunk.getContextPrefix().isBlank()
                    ? chunk.getContextPrefix() + "\n\n" + chunk.getContent()
                    : chunk.getContent();
            embedTargets.add(TextSegment.from(embedText, metadata));
        }
        List<Embedding> embeddings = embeddingModel.embedAll(embedTargets).content();
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
