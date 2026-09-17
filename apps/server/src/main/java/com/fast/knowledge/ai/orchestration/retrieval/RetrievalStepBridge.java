package com.fast.knowledge.ai.orchestration.retrieval;

import java.util.function.Consumer;

/**
 * 检索步骤消费器的线程级桥 — 流式接口在发起线程上注册，
 * 检索（ContentRetriever → RetrievalOrchestrator）在同线程同步执行时读取。
 * 使用方必须在请求处理结束（finally）调用 {@link #clear()} 防止线程池串扰。
 */
public final class RetrievalStepBridge {

    private static final ThreadLocal<Consumer<RetrievalStep>> HOLDER = new ThreadLocal<>();

    private RetrievalStepBridge() {
    }

    public static void set(Consumer<RetrievalStep> consumer) {
        HOLDER.set(consumer);
    }

    public static Consumer<RetrievalStep> get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
