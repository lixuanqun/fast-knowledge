package com.fast.knowledge.integration;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 诊断探针：实证 langchain4j OpenAiStreamingChatModel 对 DashScope 兼容流的回调行为。
 * 需环境变量 EMBEDDING/LLM key；标记 integration，不在常规单测中执行。
 */
@Tag("integration")
class DashScopeStreamProbe {

    @Test
    void probeStreamingCallbacks() throws Exception {
        String key = System.getenv("LLM_API_KEY");
        if (key == null || key.isBlank()) {
            key = System.getProperty("llm.key");
        }
        org.junit.jupiter.api.Assertions.assertNotNull(key, "需要 LLM_API_KEY");

        StreamingChatModel model = OpenAiStreamingChatModel.builder()
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .apiKey(key)
                .modelName("qwen-plus")
                .timeout(java.time.Duration.ofSeconds(60))
                .build();

        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder partials = new StringBuilder();
        model.chat(java.util.List.of(UserMessage.from("从1数到3，用中文顿号分隔")),
                new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String token) {
                        partials.append("P");
                        System.out.println("[PARTIAL] " + token);
                    }

                    @Override
                    public void onCompleteResponse(ChatResponse response) {
                        System.out.println("[COMPLETE] " + response.aiMessage().text());
                        latch.countDown();
                    }

                    @Override
                    public void onError(Throwable error) {
                        System.out.println("[ERROR] " + error);
                        latch.countDown();
                    }
                });

        boolean done = latch.await(45, TimeUnit.SECONDS);
        System.out.println("[RESULT] done=" + done + " partialCount=" + partials.length());
    }
}
