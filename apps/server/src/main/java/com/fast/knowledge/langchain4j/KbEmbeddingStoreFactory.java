package com.fast.knowledge.langchain4j;

import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.langchain4j.store.LuceneEmbeddingStore;
import com.fast.knowledge.langchain4j.store.LocalEmbeddingStore;
import com.fast.knowledge.langchain4j.store.VectorIndexLifecycle;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 知识库向量存储工厂 — 双引擎路由（knowledge.vector.engine）：
 * <ul>
 *   <li>lucene（默认）：进程内 HNSW + 段文件落盘（LuceneEmbeddingStore），大规模低内存；</li>
 *   <li>memory：内存暴力扫描 + per-KB JSON 持久化（LocalEmbeddingStore），小规模/测试。</li>
 * </ul>
 * 上层（KbEmbeddingIngestor / Search / Retrieval）只面向 EmbeddingStore 接口。
 */
@Slf4j
@Component
public class KbEmbeddingStoreFactory {

    private final KnowledgeProperties properties;
    private final Map<Long, KbEmbeddingStore> stores = new ConcurrentHashMap<>();
    private final Map<Long, VectorIndexLifecycle> lifecycles = new ConcurrentHashMap<>();

    public KbEmbeddingStoreFactory(KnowledgeProperties properties) {
        this.properties = properties;
    }

    public KbEmbeddingStore getStore(Long kbId) {
        return stores.computeIfAbsent(kbId, id -> new KbEmbeddingStore(id, resolveDelegate(id), properties));
    }

    /** 逐出缓存；落盘并释放底层资源，防止丢数据 */
    public void evict(Long kbId) {
        VectorIndexLifecycle lifecycle = lifecycles.remove(kbId);
        if (lifecycle != null) {
            lifecycle.close();
        }
        stores.remove(kbId);
    }

    @PreDestroy
    public void flushAll() {
        lifecycles.values().forEach(VectorIndexLifecycle::flush);
    }

    private EmbeddingStore<TextSegment> resolveDelegate(Long kbId) {
        String engine = properties.getVector().getEngine();
        if ("memory".equalsIgnoreCase(engine)) {
            Path file = Path.of(properties.getVector().getLocal().getStorageDir(), "kb-" + kbId + ".json");
            LocalEmbeddingStore store = LocalEmbeddingStore.load(file);
            lifecycles.put(kbId, store);
            return store;
        }
        Path dir = Path.of(properties.getVector().getLocal().getStorageDir(), "lucene", "kb-" + kbId);
        LuceneEmbeddingStore store = LuceneEmbeddingStore.open(dir);
        lifecycles.put(kbId, store);
        log.info("向量引擎: lucene(HNSW) kbId={} dir={}", kbId, dir);
        return store;
    }
}
