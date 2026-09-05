package com.fast.knowledge.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;

/**
 * OpenAI 兼容云端向量模型（/v1/embeddings）。
 * 覆盖 DashScope compatible-mode、硅基流动、OpenAI 等兼容端点。
 */
@Slf4j
public class OpenAiEmbeddingProvider implements EmbeddingProvider {

    private final OpenAiEmbeddingModel model;
    private final int dimension;

    public OpenAiEmbeddingProvider(String baseUrl, String apiKey, String modelName, int dimension) {
        this.model = OpenAiEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(60))
                .build();
        this.dimension = dimension;
        log.info("OpenAI 兼容向量模型已装配: baseUrl={}, model={}, dimension={}", baseUrl, modelName, dimension);
    }

    @Override
    public float[] embed(String text) {
        return model.embed(text).content().vector();
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        List<TextSegment> segments = texts.stream().map(TextSegment::from).toList();
        return model.embedAll(segments).content().stream()
                .map(Embedding::vector)
                .toList();
    }

    @Override
    public int dimension() {
        return dimension;
    }
}
