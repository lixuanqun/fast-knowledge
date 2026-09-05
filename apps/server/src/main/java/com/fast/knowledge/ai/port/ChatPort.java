package com.fast.knowledge.ai.port;

/**
 * 单轮生成端口 — LLM 补全与流式生成的最小领域面。
 * 实现方负责模型解析与热刷新（见 LlmModelRegistry）。
 */
public interface ChatPort {

    /** 单轮补全；systemPrompt 允许为 null */
    String complete(String systemPrompt, String userPrompt);

    /** 流式生成；调用即启动，回调在实现方线程上触发 */
    void stream(String systemPrompt, String userPrompt, StreamHandler handler);

    interface StreamHandler {
        void onPartial(String token);
        void onComplete(String fullText);
        void onError(Throwable error);
    }
}
