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
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 持有可热刷新的 ChatModel / StreamingChatModel，并作为 Spring Bean 对外委托。
 * <p>使用读写锁：高并发读（get）无阻塞；写（refresh）独占。
 */
@Component
public class LlmModelRegistry {

    private final LlmConfigResolver llmConfigResolver;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private volatile ChatModel chatModelDelegate;
    private volatile StreamingChatModel streamingChatModelDelegate;

    public LlmModelRegistry(LlmConfigResolver llmConfigResolver) {
        this.llmConfigResolver = llmConfigResolver;
    }

    @PostConstruct
    void init() {
        refresh();
    }

    public void refresh() {
        lock.writeLock().lock();
        try {
            ResolvedLlmConfig cfg = llmConfigResolver.resolve();
            ChatModel newChat = buildChatModel(cfg);
            StreamingChatModel newStreaming = buildStreamingChatModel(cfg);
            this.chatModelDelegate = newChat;
            this.streamingChatModelDelegate = newStreaming;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public ChatModel getChatModel() {
        lock.readLock().lock();
        try {
            ChatModel delegate = chatModelDelegate;
            if (delegate == null) {
                throw new BusinessException("LLM 尚未初始化");
            }
            return delegate;
        } finally {
            lock.readLock().unlock();
        }
    }

    public StreamingChatModel getStreamingChatModel() {
        lock.readLock().lock();
        try {
            StreamingChatModel delegate = streamingChatModelDelegate;
            if (delegate == null) {
                throw new BusinessException("LLM 尚未初始化");
            }
            return delegate;
        } finally {
            lock.readLock().unlock();
        }
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
                throw new BusinessException("LLM 返回空响应，请检查模型配置");
            }
            return "连接成功";
        } catch (BusinessException ex) {
            throw ex;
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
