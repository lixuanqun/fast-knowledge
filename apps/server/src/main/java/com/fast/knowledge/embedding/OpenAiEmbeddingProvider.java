package com.fast.knowledge.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
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
                .dimensions(dimension)
                .timeout(Duration.ofSeconds(60))
                .build();
        this.dimension = dimension;
        log.info("OpenAI 兼容向量模型已装配: baseUrl={}, model={}, dimension={}", baseUrl, modelName, dimension);
    }

    /** DashScope text-embedding 单请求 input 上限 10 条（OpenAI 官方上限更高，统一取严） */
    private static final int MAX_BATCH = 10;

    @Override
    public float[] embed(String text) {
        return model.embed(text).content().vector();
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> result = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i += MAX_BATCH) {
            List<String> batch = texts.subList(i, Math.min(i + MAX_BATCH, texts.size()));
            List<TextSegment> segments = batch.stream().map(TextSegment::from).toList();
            model.embedAll(segments).content().stream()
                    .map(Embedding::vector)
                    .forEach(result::add);
        }
        return result;
    }

    @Override
    public int dimension() {
        return dimension;
    }
}
