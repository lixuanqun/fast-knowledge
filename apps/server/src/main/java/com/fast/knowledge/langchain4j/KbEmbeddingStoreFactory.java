package com.fast.knowledge.langchain4j;

import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.vector.VectorStore;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class KbEmbeddingStoreFactory {

    private final VectorStore vectorStore;
    private final KnowledgeProperties properties;
    private final Map<Long, KbEmbeddingStore> stores = new ConcurrentHashMap<>();

    public KbEmbeddingStoreFactory(VectorStore vectorStore, KnowledgeProperties properties) {
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    public KbEmbeddingStore getStore(Long kbId) {
        return stores.computeIfAbsent(kbId, id -> new KbEmbeddingStore(id, vectorStore, properties));
    }

    public void evict(Long kbId) {
        stores.remove(kbId);
    }
}
