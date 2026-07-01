package com.fast.knowledge.embedding;

import com.fast.knowledge.config.KnowledgeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Configuration
public class EmbeddingConfig {

    @Bean
    @Primary
    public EmbeddingProvider embeddingProvider(KnowledgeProperties properties,
                                               HashEmbeddingProvider hashProvider,
                                               ObjectProvider<OllamaEmbeddingProvider> ollamaProvider) {
        String provider = properties.getEmbedding().getProvider();
        if ("ollama".equalsIgnoreCase(provider)) {
            OllamaEmbeddingProvider ollama = ollamaProvider.getIfAvailable();
            if (ollama != null) {
                log.info("Embedding provider: ollama ({})", properties.getEmbedding().getOllamaModel());
                return ollama;
            }
            log.warn("已配置 EMBEDDING_PROVIDER=ollama 但 Ollama 不可用，尝试其他 provider");
        }
        if ("onnx".equalsIgnoreCase(provider)) {
            Path modelPath = Path.of(properties.getEmbedding().getOnnxModelPath());
            if (Files.exists(modelPath)) {
                log.info("Embedding provider: onnx ({})", modelPath.toAbsolutePath());
                return new OnnxEmbeddingProvider(properties);
            }
            log.warn("已配置 EMBEDDING_PROVIDER=onnx 但模型文件不存在: {}，尝试其他 provider",
                    modelPath.toAbsolutePath());
        }
        if ("hash".equalsIgnoreCase(provider)) {
            log.warn("Embedding provider: hash（仅适合开发/演示，生产请使用 onnx 或 ollama）");
            return hashProvider;
        }
        log.warn("Embedding provider 回退为 hash（配置值: {}）", provider);
        return hashProvider;
    }
}
