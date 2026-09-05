package com.fast.knowledge.langchain4j.store;

import com.fast.knowledge.langchain4j.KbEmbeddingStore;
import com.fast.knowledge.langchain4j.KbEmbeddingStoreFactory;
import dev.langchain4j.store.embedding.filter.Filter;
import org.springframework.stereotype.Service;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * 向量索引维护（按元数据删除）—— 经 {@link KbEmbeddingStoreFactory} 双轨路由
 * （本地文件向量索引按元数据过滤删除）。
 */
@Service
public class KbVectorIndexService {

    private final KbEmbeddingStoreFactory storeFactory;

    public KbVectorIndexService(KbEmbeddingStoreFactory storeFactory) {
        this.storeFactory = storeFactory;
    }

    public void deleteByDocument(Long kbId, Long docId) {
        storeFactory.getStore(kbId).removeAll(kbAndDocFilter(kbId, docId));
    }

    public void deleteChunk(Long kbId, Long chunkId) {
        storeFactory.getStore(kbId).removeAll(metadataKey(KbEmbeddingStore.META_KB_ID).isEqualTo(kbId)
                .and(metadataKey(KbEmbeddingStore.META_CHUNK_ID).isEqualTo(chunkId)));
    }

    public void deleteKb(Long kbId) {
        storeFactory.getStore(kbId).removeAll(metadataKey(KbEmbeddingStore.META_KB_ID).isEqualTo(kbId));
        // 同步落盘空库并逐出缓存
        storeFactory.evict(kbId);
    }

    private Filter kbAndDocFilter(Long kbId, Long docId) {
        return metadataKey(KbEmbeddingStore.META_KB_ID).isEqualTo(kbId)
                .and(metadataKey(KbEmbeddingStore.META_DOC_ID).isEqualTo(docId));
    }
}
