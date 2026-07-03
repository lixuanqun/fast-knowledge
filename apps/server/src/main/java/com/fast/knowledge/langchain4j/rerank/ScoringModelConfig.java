package com.fast.knowledge.langchain4j.rerank;



import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.security.ExternalAccessGuard;

import dev.langchain4j.model.cohere.CohereScoringModel;

import dev.langchain4j.model.jina.JinaScoringModel;

import dev.langchain4j.model.scoring.ScoringModel;

import lombok.extern.slf4j.Slf4j;

import org.springframework.context.annotation.Bean;

import org.springframework.context.annotation.Configuration;



import java.nio.file.Files;

import java.nio.file.Path;

import java.time.Duration;

import java.util.Optional;



/**

 * 可选 Reranker（ScoringModel）：ONNX 本地 / Cohere / Jina 云端 API。

 * 默认关闭；开启后检索先召回更多候选，再重排序截取 topK。

 */

@Slf4j

@Configuration

public class ScoringModelConfig {



    @Bean

    public Optional<ScoringModel> scoringModel(KnowledgeProperties properties,
                                               ExternalAccessGuard externalAccessGuard) {

        KnowledgeProperties.Rerank rerank = properties.getSearch().getRerank();

        if (!rerank.isEnabled()) {

            return Optional.empty();

        }

        String provider = rerank.getProvider() != null ? rerank.getProvider().trim().toLowerCase() : "none";

        return switch (provider) {

            case "onnx" -> buildOnnx(rerank);

            case "cohere" -> buildCohere(rerank, properties, externalAccessGuard);

            case "jina" -> buildJina(rerank, properties, externalAccessGuard);

            default -> {

                log.warn("Rerank 已启用但 provider={} 不受支持，已忽略", provider);

                yield Optional.empty();

            }

        };

    }



    private Optional<ScoringModel> buildOnnx(KnowledgeProperties.Rerank rerank) {

        Path modelPath = Path.of(rerank.getOnnxModelPath());

        if (!Files.exists(modelPath)) {

            log.warn("Rerank provider=onnx 但模型文件不存在: {}", modelPath.toAbsolutePath());

            return Optional.empty();

        }

        try {

            log.info("Rerank provider: onnx ({})", modelPath.toAbsolutePath());

            return Optional.of(new OnnxRerankScoringModel(rerank));

        } catch (Exception e) {

            log.warn("加载 ONNX Reranker 失败: {}", e.getMessage());

            return Optional.empty();

        }

    }



    private Optional<ScoringModel> buildCohere(KnowledgeProperties.Rerank rerank,
                                               KnowledgeProperties properties,
                                               ExternalAccessGuard externalAccessGuard) {

        if (!properties.getLlm().isAllowExternal()) {
            log.warn("Rerank provider=cohere 需要外连 API，但 knowledge.llm.allow-external=false，已忽略");
            return Optional.empty();
        }

        String apiKey = firstNonBlank(rerank.getCohereApiKey(), System.getenv("COHERE_API_KEY"));

        if (apiKey == null || apiKey.isBlank()) {

            log.warn("Rerank provider=cohere 但未配置 COHERE_API_KEY / knowledge.search.rerank.cohere-api-key");

            return Optional.empty();

        }

        externalAccessGuard.validateHttpEndpoint("https://api.cohere.com", "Rerank(Cohere)");

        log.info("Rerank provider: cohere ({})", rerank.getCohereModel());

        return Optional.of(CohereScoringModel.builder()

                .apiKey(apiKey)

                .modelName(rerank.getCohereModel())

                .timeout(Duration.ofSeconds(30))

                .build());

    }



    private Optional<ScoringModel> buildJina(KnowledgeProperties.Rerank rerank,
                                             KnowledgeProperties properties,
                                             ExternalAccessGuard externalAccessGuard) {

        if (!properties.getLlm().isAllowExternal()) {
            log.warn("Rerank provider=jina 需要外连 API，但 knowledge.llm.allow-external=false，已忽略");
            return Optional.empty();
        }

        String apiKey = firstNonBlank(rerank.getJinaApiKey(), System.getenv("JINA_API_KEY"));

        if (apiKey == null || apiKey.isBlank()) {

            log.warn("Rerank provider=jina 但未配置 JINA_API_KEY / knowledge.search.rerank.jina-api-key");

            return Optional.empty();

        }

        externalAccessGuard.validateHttpEndpoint("https://api.jina.ai", "Rerank(Jina)");

        log.info("Rerank provider: jina ({})", rerank.getJinaModel());

        return Optional.of(JinaScoringModel.builder()

                .apiKey(apiKey)

                .modelName(rerank.getJinaModel())

                .timeout(Duration.ofSeconds(30))

                .build());

    }



    private static String firstNonBlank(String a, String b) {

        if (a != null && !a.isBlank()) {

            return a;

        }

        return b;

    }

}


