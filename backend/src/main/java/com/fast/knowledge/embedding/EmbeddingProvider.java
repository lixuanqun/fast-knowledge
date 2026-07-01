package com.fast.knowledge.embedding;

import java.util.List;

public interface EmbeddingProvider {
    float[] embed(String text);
    List<float[]> embedBatch(List<String> texts);
    int dimension();
}
