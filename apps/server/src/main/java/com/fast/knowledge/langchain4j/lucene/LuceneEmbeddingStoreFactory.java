package com.fast.knowledge.langchain4j.lucene;

import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.vector.VectorStore;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LuceneEmbeddingStoreFactory {

    private final VectorStore vectorStore;
    private final KnowledgeProperties properties;
    private final Map<Long, LuceneEmbeddingStore> stores = new ConcurrentHashMap<>();

    public LuceneEmbeddingStoreFactory(VectorStore vectorStore, KnowledgeProperties properties) {
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    public LuceneEmbeddingStore getStore(Long kbId) {
        return stores.computeIfAbsent(kbId, id -> new LuceneEmbeddingStore(id, vectorStore, properties));
    }

    public void evict(Long kbId) {
        stores.remove(kbId);
    }
}
