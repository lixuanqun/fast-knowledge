package com.fast.knowledge.embedding;

import com.fast.knowledge.config.KnowledgeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Slf4j
@Configuration
public class EmbeddingConfig {

    @Bean
    @Primary
    public EmbeddingProvider embeddingProvider(KnowledgeProperties properties,
                                               HashEmbeddingProvider hashProvider,
                                               ObjectProvider<OllamaEmbeddingProvider> ollamaProvider) {
        KnowledgeProperties.Embedding embedding = properties.getEmbedding();
        String provider = embedding.getProvider();
        if ("openai".equalsIgnoreCase(provider)) {
            if (embedding.getOpenaiApiKey() == null || embedding.getOpenaiApiKey().isBlank()) {
                log.warn("Embedding provider=openai 但未配置 API Key（knowledge.embedding.openai-api-key），回退 hash（仅开发用）");
                return hashProvider;
            }
            log.info("Embedding provider: openai ({}, {})", embedding.getOpenaiBaseUrl(), embedding.getOpenaiModel());
            return new OpenAiEmbeddingProvider(
                    embedding.getOpenaiBaseUrl(),
                    embedding.getOpenaiApiKey(),
                    embedding.getOpenaiModel(),
                    embedding.getDimension());
        }
        if ("ollama".equalsIgnoreCase(provider)) {
            OllamaEmbeddingProvider ollama = ollamaProvider.getIfAvailable();
            if (ollama != null) {
                log.info("Embedding provider: ollama ({})", embedding.getOllamaModel());
                return ollama;
            }
            log.warn("已配置 EMBEDDING_PROVIDER=ollama 但 Ollama 不可用，尝试其他 provider");
        }
        if ("hash".equalsIgnoreCase(provider)) {
            log.warn("Embedding provider: hash（仅适合开发/演示，生产请使用 openai 或 ollama）");
            return hashProvider;
        }
        log.warn("Embedding provider 回退为 hash（配置值: {}）", provider);
        return hashProvider;
    }
}
