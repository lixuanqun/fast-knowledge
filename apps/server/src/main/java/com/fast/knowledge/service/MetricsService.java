package com.fast.knowledge.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 可观测性指标服务 — 预创建固定维度 Meter，避免高基数时间序列。
 *
 * <p>按环节分段计时，支持检索链路各阶段独立观测：
 * <ul>
 *   <li>kb.search.embedding.latency — Embedding 向量化延迟</li>
 *   <li>kb.search.vector.latency — pgvector 检索延迟</li>
 *   <li>kb.search.rerank.latency — 重排序延迟</li>
 *   <li>kb.search.latency — 检索全链路延迟</li>
 *   <li>kb.rag.latency — RAG 全链路延迟（检索+生成）</li>
 *   <li>kb.chat.first_token.latency — 流式对话首 token 延迟</li>
 *   <li>kb.index.duration — 文档索引耗时</li>
 * </ul>
 */
@Service
public class MetricsService {

    private final MeterRegistry registry;

    // --- Search segment timers ---
    private final Timer embeddingLatency;
    private final Timer vectorSearchLatency;
    private final Timer rerankLatency;
    private final Timer searchLatency;
    private final Counter searchCount;

    // --- Search hits ---
    private final Counter searchHitsZero;
    private final Counter searchHitsLow;
    private final Counter searchHitsMid;
    private final Counter searchHitsHigh;

    // --- Cache ---
    private final Counter cacheHit;
    private final Counter cacheMiss;

    // --- RAG ---
    private final Timer ragLatency;
    private final Counter ragCount;

    // --- Chat ---
    private final Timer firstTokenLatency;

    // --- Index ---
    private final Timer indexDuration;
    private final Counter indexCount;

    // --- In-memory for gauge registration ---
    private final AtomicLong cacheHitCount = new AtomicLong();
    private final AtomicLong cacheMissCount = new AtomicLong();

    public MetricsService(MeterRegistry registry) {
        this.registry = registry;

        this.embeddingLatency = Timer.builder("kb.search.embedding.latency")
                .description("Embedding 向量化延迟").register(registry);
        this.vectorSearchLatency = Timer.builder("kb.search.vector.latency")
                .description("pgvector 检索延迟").register(registry);
        this.rerankLatency = Timer.builder("kb.search.rerank.latency")
                .description("重排序延迟").register(registry);
        this.searchLatency = Timer.builder("kb.search.latency")
                .description("检索全链路延迟")
                .publishPercentiles(0.5, 0.95)
                .register(registry);
        this.searchCount = Counter.builder("kb.search.count")
                .description("检索请求数").register(registry);

        this.searchHitsZero = Counter.builder("kb.search.hits")
                .description("检索命中数").tag("range", "0").register(registry);
        this.searchHitsLow = Counter.builder("kb.search.hits")
                .description("检索命中数").tag("range", "1-5").register(registry);
        this.searchHitsMid = Counter.builder("kb.search.hits")
                .description("检索命中数").tag("range", "6-20").register(registry);
        this.searchHitsHigh = Counter.builder("kb.search.hits")
                .description("检索命中数").tag("range", "20+").register(registry);

        this.cacheHit = Counter.builder("kb.search.cache")
                .description("检索缓存").tag("result", "hit").register(registry);
        this.cacheMiss = Counter.builder("kb.search.cache")
                .description("检索缓存").tag("result", "miss").register(registry);

        this.ragLatency = Timer.builder("kb.rag.latency")
                .description("RAG 全链路延迟（检索+生成）")
                .publishPercentiles(0.5, 0.95)
                .register(registry);
        this.ragCount = Counter.builder("kb.rag.count")
                .description("RAG 问答请求数").register(registry);

        this.firstTokenLatency = Timer.builder("kb.chat.first_token.latency")
                .description("流式对话首 token 延迟").register(registry);

        this.indexDuration = Timer.builder("kb.index.duration")
                .description("文档索引耗时").register(registry);
        this.indexCount = Counter.builder("kb.index.count")
                .description("已索引分块数").register(registry);

        // Register cache gauge
        io.micrometer.core.instrument.Gauge.builder("kb.search.cache.hit_rate", this,
                s -> s.cacheHitCount.get() + s.cacheMissCount.get() > 0
                        ? (double) s.cacheHitCount.get() / (s.cacheHitCount.get() + s.cacheMissCount.get())
                        : 0.0)
                .description("检索缓存命中率").register(registry);
    }

    // ---- Search segment timing ----

    public <T> T timeEmbedding(Callable<T> action) {
        return record(embeddingLatency, action);
    }

    public <T> T timeVectorSearch(Callable<T> action) {
        return record(vectorSearchLatency, action);
    }

    public <T> T timeRerank(Callable<T> action) {
        return record(rerankLatency, action);
    }

    public <T> T timeSearch(Callable<T> action) {
        return record(searchLatency, action);
    }

    public void countSearch() {
        searchCount.increment();
    }

    // ---- Search hits ----

    public void countSearchHits(int hitCount) {
        if (hitCount == 0) searchHitsZero.increment();
        else if (hitCount <= 5) searchHitsLow.increment();
        else if (hitCount <= 20) searchHitsMid.increment();
        else searchHitsHigh.increment();
    }

    // ---- Cache ----

    public void recordCacheHit() {
        cacheHit.increment();
        cacheHitCount.incrementAndGet();
    }

    public void recordCacheMiss() {
        cacheMiss.increment();
        cacheMissCount.incrementAndGet();
    }

    // ---- RAG ----

    public <T> T timeRag(Callable<T> action) {
        return record(ragLatency, action);
    }

    public void countRag() {
        ragCount.increment();
    }

    // ---- Chat ----

    /** 记录流式对话的首次 token 延迟（毫秒） */
    public void recordFirstTokenLatency(long latencyMs) {
        firstTokenLatency.record(latencyMs, TimeUnit.MILLISECONDS);
    }

    // ---- Index ----

    public <T> T timeIndex(Callable<T> action) {
        return record(indexDuration, action);
    }

    public void countIndex(int chunkCount) {
        indexCount.increment(chunkCount);
    }

    // ---- LLM ----

    public void countLlmCall(String purpose) {
        Counter.builder("kb.llm.calls")
                .description("LLM 调用次数")
                .tag("purpose", purpose)
                .register(registry)
                .increment();
    }

    // ---- Query Rewrite ----

    public void countQueryRewrite(boolean rewritten) {
        Counter.builder("kb.query.rewrite.count")
                .description("查询改写调用次数")
                .tag("rewritten", String.valueOf(rewritten))
                .register(registry)
                .increment();
    }

    public void countAgentic(int subQueryCount) {
        Counter.builder("kb.rag.agentic")
                .description("Agentic 多跳召回")
                .tag("sub_queries", String.valueOf(Math.min(Math.max(subQueryCount, 1), 4)))
                .register(registry)
                .increment();
    }

    public MeterRegistry registry() {
        return registry;
    }

    private static <T> T record(Timer timer, Callable<T> action) {
        try {
            return timer.recordCallable(action);
        } catch (Exception e) {
            if (e instanceof RuntimeException re) {
                throw re; // rethrow BusinessException etc. as-is
            }
            throw new RuntimeException(e);
        }
    }
}
