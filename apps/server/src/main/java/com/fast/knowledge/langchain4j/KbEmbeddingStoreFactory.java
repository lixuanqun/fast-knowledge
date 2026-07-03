package com.fast.knowledge.langchain4j;

import com.fast.knowledge.config.KnowledgeProperties;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class KbEmbeddingStoreFactory {

    private final PgVectorEmbeddingStore embeddingStore;
    private final KnowledgeProperties properties;
    private final Map<Long, KbEmbeddingStore> stores = new ConcurrentHashMap<>();

    public KbEmbeddingStoreFactory(PgVectorEmbeddingStore embeddingStore, KnowledgeProperties properties) {
        this.embeddingStore = embeddingStore;
        this.properties = properties;
    }

    public KbEmbeddingStore getStore(Long kbId) {
        return stores.computeIfAbsent(kbId, id -> new KbEmbeddingStore(id, embeddingStore, properties));
    }

    public void evict(Long kbId) {
        stores.remove(kbId);
    }
}
