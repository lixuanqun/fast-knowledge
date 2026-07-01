package com.fast.knowledge.embedding;

import com.fast.knowledge.config.KnowledgeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 当 ONNX/Ollama 不可用时的降级实现，保证系统可启动与联调。
 * 生产环境请配置 ONNX 模型或 Ollama Embedding。
 */
@Slf4j
@Component
public class HashEmbeddingProvider implements EmbeddingProvider {

    private final int dimension;

    public HashEmbeddingProvider(KnowledgeProperties properties) {
        this.dimension = properties.getEmbedding().getDimension();
        log.warn("使用 HashEmbeddingProvider 降级模式，语义检索质量有限，请配置 ONNX 或 Ollama");
    }

    @Override
    public float[] embed(String text) {
        return hashToVector(text);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> result = new ArrayList<>();
        for (String text : texts) {
            result.add(hashToVector(text));
        }
        return result;
    }

    @Override
    public int dimension() {
        return dimension;
    }

    private float[] hashToVector(String text) {
        float[] vec = new float[dimension];
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < bytes.length; i++) {
            int idx = Math.floorMod((bytes[i] & 0xFF) * 31 + i, dimension);
            vec[idx] += (bytes[i] & 0xFF) / 255.0f;
        }
        float norm = 0;
        for (float v : vec) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < vec.length; i++) {
                vec[i] /= norm;
            }
        }
        return vec;
    }
}
