package com.fast.knowledge.llm;

import com.fast.knowledge.common.BusinessException;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 持有可热刷新的 ChatModel / StreamingChatModel，并作为 Spring Bean 对外委托。
 */
@Component
public class LlmModelRegistry {

    private final LlmConfigResolver llmConfigResolver;

    private volatile ChatModel chatModelDelegate;
    private volatile StreamingChatModel streamingChatModelDelegate;

    public LlmModelRegistry(LlmConfigResolver llmConfigResolver) {
        this.llmConfigResolver = llmConfigResolver;
    }

    @PostConstruct
    void init() {
        refresh();
    }

    public synchronized void refresh() {
        ResolvedLlmConfig cfg = llmConfigResolver.resolve();
        ChatModel newChat = buildChatModel(cfg);
        StreamingChatModel newStreaming = buildStreamingChatModel(cfg);
        this.chatModelDelegate = newChat;
        this.streamingChatModelDelegate = newStreaming;
    }

    public ChatModel getChatModel() {
        ChatModel delegate = chatModelDelegate;
        if (delegate == null) {
            throw new BusinessException("LLM 尚未初始化");
        }
        return delegate;
    }

    public StreamingChatModel getStreamingChatModel() {
        StreamingChatModel delegate = streamingChatModelDelegate;
        if (delegate == null) {
            throw new BusinessException("LLM 尚未初始化");
        }
        return delegate;
    }

    public static ChatModel buildChatModel(ResolvedLlmConfig cfg) {
        return OpenAiChatModel.builder()
                .baseUrl(cfg.getBaseUrl())
                .apiKey(cfg.getApiKey())
                .modelName(cfg.getModel())
                .maxTokens(cfg.getMaxTokens())
                .temperature(cfg.getTemperature())
                .timeout(Duration.ofMinutes(3))
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    public static StreamingChatModel buildStreamingChatModel(ResolvedLlmConfig cfg) {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(cfg.getBaseUrl())
                .apiKey(cfg.getApiKey())
                .modelName(cfg.getModel())
                .maxTokens(cfg.getMaxTokens())
                .temperature(cfg.getTemperature())
                .timeout(Duration.ofMinutes(3))
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    public String testConnectivity(ResolvedLlmConfig cfg) {
        try {
            ChatModel probe = buildChatModel(cfg);
            ChatResponse response = probe.chat(UserMessage.from("ping"));
            String text = response.aiMessage() != null ? response.aiMessage().text() : "";
            if (text == null || text.isBlank()) {
                return "连接成功";
            }
            return "连接成功";
        } catch (Exception e) {
            throw new BusinessException("LLM 连接测试失败: " + rootMessage(e));
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable current = e;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message != null && !message.isBlank() ? message : current.getClass().getSimpleName();
    }
}
