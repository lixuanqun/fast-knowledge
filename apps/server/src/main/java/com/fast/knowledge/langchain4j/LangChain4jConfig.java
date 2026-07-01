package com.fast.knowledge.langchain4j;

import com.fast.knowledge.embedding.EmbeddingProvider;
import com.fast.knowledge.llm.LlmConfigResolver;
import com.fast.knowledge.llm.ResolvedLlmConfig;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class LangChain4jConfig {

    @Bean
    public EmbeddingModel embeddingModel(EmbeddingProvider embeddingProvider) {
        return new ProviderEmbeddingModel(embeddingProvider);
    }

    @Bean
    public ChatModel chatModel(LlmConfigResolver llmConfigResolver) {
        ResolvedLlmConfig cfg = llmConfigResolver.resolve();
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

    @Bean
    public StreamingChatModel streamingChatModel(LlmConfigResolver llmConfigResolver) {
        ResolvedLlmConfig cfg = llmConfigResolver.resolve();
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

    @Bean
    public KnowledgeAssistant knowledgeAssistant(ChatModel chatModel) {
        return AiServices.builder(KnowledgeAssistant.class)
                .chatModel(chatModel)
                .build();
    }
}
