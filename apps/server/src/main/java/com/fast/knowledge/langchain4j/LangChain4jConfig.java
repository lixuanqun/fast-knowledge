package com.fast.knowledge.langchain4j;

import com.fast.knowledge.embedding.EmbeddingProvider;
import com.fast.knowledge.llm.DelegatingChatModel;
import com.fast.knowledge.llm.DelegatingStreamingChatModel;
import com.fast.knowledge.llm.LlmModelRegistry;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LangChain4jConfig {

    @Bean
    public EmbeddingModel embeddingModel(EmbeddingProvider embeddingProvider) {
        return new ProviderEmbeddingModel(embeddingProvider);
    }

    @Bean
    public ChatModel chatModel(LlmModelRegistry llmModelRegistry) {
        return new DelegatingChatModel(llmModelRegistry);
    }

    @Bean
    public StreamingChatModel streamingChatModel(LlmModelRegistry llmModelRegistry) {
        return new DelegatingStreamingChatModel(llmModelRegistry);
    }
}
