package com.fast.knowledge.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.concurrent.Callable;

/**
 * 可观测性指标服务 — 预创建固定维度 Meter，避免每个 kbId 产生独立时间序列。
 *
 * <p>设计原则：按 kbId 分维度会导致指标基数爆炸，改为仅记录全局计数器。
 * 按模型、用途等低基数标签分维。如需按知识库粒度分析，由外部日志/指标系统聚合。
 */
@Service
public class MetricsService {

    private final MeterRegistry registry;
    private final Timer searchLatency;
    private final Counter searchCount;
    private final Counter searchHitsZero;
    private final Counter searchHitsLow;
    private final Counter searchHitsMid;
    private final Counter searchHitsHigh;
    private final Timer ragLatency;
    private final Counter ragCount;
    private final Timer indexDuration;
    private final Counter indexCount;
    private final Counter llmCalls;

    public MetricsService(MeterRegistry registry) {
        this.registry = registry;
        this.searchLatency = Timer.builder("kb.search.latency")
                .description("知识库检索延迟").register(registry);
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
        this.ragLatency = Timer.builder("kb.rag.latency")
                .description("RAG 全链路延迟（检索+生成）").register(registry);
        this.ragCount = Counter.builder("kb.rag.count")
                .description("RAG 问答请求数").register(registry);
        this.indexDuration = Timer.builder("kb.index.duration")
                .description("文档索引耗时").register(registry);
        this.indexCount = Counter.builder("kb.index.count")
                .description("已索引分块数").register(registry);
        this.llmCalls = Counter.builder("kb.llm.calls")
                .description("LLM 调用次数").tag("model", "auto").tag("purpose", "auto").register(registry);
    }

    /** 记录检索延迟（自动计时），支持受检异常 */
    public <T> T timeSearch(Callable<T> action) {
        try {
            return searchLatency.recordCallable(action);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 记录检索次数 */
    public void countSearch() {
        searchCount.increment();
    }

    /** 记录检索命中数（按区间分桶） */
    public void countSearchHits(int hitCount) {
        if (hitCount == 0) searchHitsZero.increment();
        else if (hitCount <= 5) searchHitsLow.increment();
        else if (hitCount <= 20) searchHitsMid.increment();
        else searchHitsHigh.increment();
    }

    /** 记录 RAG 全链路延迟，支持受检异常 */
    public <T> T timeRag(Callable<T> action) {
        try {
            return ragLatency.recordCallable(action);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 记录 RAG 问答次数 */
    public void countRag() {
        ragCount.increment();
    }

    /** 记录索引耗时，支持受检异常 */
    public <T> T timeIndex(Callable<T> action) {
        try {
            return indexDuration.recordCallable(action);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 记录索引分块数 */
    public void countIndex(int chunkCount) {
        indexCount.increment(chunkCount);
    }

    /** 记录 LLM 调用（按目的分类） */
    public void countLlmCall(String purpose) {
        Counter.builder("kb.llm.calls")
                .description("LLM 调用次数")
                .tag("purpose", purpose)
                .register(registry)
                .increment();
    }

    /** 记录 Query Rewrite 调用 */
    public void countQueryRewrite(boolean rewritten) {
        Counter.builder("kb.query.rewrite.count")
                .description("查询改写调用次数")
                .tag("rewritten", String.valueOf(rewritten))
                .register(registry)
                .increment();
    }

    /** 获取注册表实例用于自定义指标 */
    public MeterRegistry registry() {
        return registry;
    }
}
