package com.fast.knowledge.langchain4j;

import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.langchain4j.store.LocalEmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 知识库向量存储工厂 — 双轨：
 * <ul>
 *   <li>provider=pgvector（Standard 形态）：PgVectorEmbeddingStore（HYBRID + HNSW）</li>
 *   <li>provider=local（Classic 形态，MySQL 部署默认）：LocalEmbeddingStore（本地文件 + 内存检索）</li>
 * </ul>
 * 上层（KbEmbeddingIngestor / Search / Retrieval）只面向 EmbeddingStore 接口，无感知差异。
 */
@Component
public class KbEmbeddingStoreFactory {

    private final ObjectProvider<PgVectorEmbeddingStore> pgStoreProvider;
    private final KnowledgeProperties properties;
    private final Map<Long, KbEmbeddingStore> stores = new ConcurrentHashMap<>();
    private final Map<Long, LocalEmbeddingStore> localStores = new ConcurrentHashMap<>();

    public KbEmbeddingStoreFactory(ObjectProvider<PgVectorEmbeddingStore> pgStoreProvider,
                                   KnowledgeProperties properties) {
        this.pgStoreProvider = pgStoreProvider;
        this.properties = properties;
    }

    public KbEmbeddingStore getStore(Long kbId) {
        return stores.computeIfAbsent(kbId, id -> new KbEmbeddingStore(id, resolveDelegate(id), properties));
    }

    /** 逐出缓存；local 形态下先同步落盘防止丢数据 */
    public void evict(Long kbId) {
        LocalEmbeddingStore local = localStores.remove(kbId);
        if (local != null) {
            local.flush();
        }
        stores.remove(kbId);
    }

    @PreDestroy
    public void flushAll() {
        localStores.values().forEach(LocalEmbeddingStore::flush);
    }

    private EmbeddingStore<TextSegment> resolveDelegate(Long kbId) {
        if ("local".equalsIgnoreCase(properties.getVector().getProvider())) {
            return localStores.computeIfAbsent(kbId, id -> {
                Path file = Path.of(properties.getVector().getLocal().getStorageDir(), "kb-" + id + ".json");
                return LocalEmbeddingStore.load(file);
            });
        }
        return pgStoreProvider.getObject();
    }
}
