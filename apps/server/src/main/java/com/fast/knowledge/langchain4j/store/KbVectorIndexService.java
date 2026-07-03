package com.fast.knowledge.langchain4j.store;

import com.fast.knowledge.langchain4j.KbEmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.stereotype.Service;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * 基于 LangChain4j PgVectorEmbeddingStore 的向量索引维护（按元数据删除）。
 */
@Service
public class KbVectorIndexService {

    private final PgVectorEmbeddingStore embeddingStore;

    public KbVectorIndexService(PgVectorEmbeddingStore embeddingStore) {
        this.embeddingStore = embeddingStore;
    }

    public void deleteByDocument(Long kbId, Long docId) {
        embeddingStore.removeAll(kbAndDocFilter(kbId, docId));
    }

    public void deleteChunk(Long kbId, Long chunkId) {
        embeddingStore.removeAll(metadataKey(KbEmbeddingStore.META_KB_ID).isEqualTo(kbId)
                .and(metadataKey(KbEmbeddingStore.META_CHUNK_ID).isEqualTo(chunkId)));
    }

    public void deleteKb(Long kbId) {
        embeddingStore.removeAll(metadataKey(KbEmbeddingStore.META_KB_ID).isEqualTo(kbId));
    }

    private Filter kbAndDocFilter(Long kbId, Long docId) {
        return metadataKey(KbEmbeddingStore.META_KB_ID).isEqualTo(kbId)
                .and(metadataKey(KbEmbeddingStore.META_DOC_ID).isEqualTo(docId));
    }
}
