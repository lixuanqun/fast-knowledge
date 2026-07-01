package com.fast.knowledge.langchain4j;

import com.fast.knowledge.embedding.EmbeddingProvider;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

import java.util.List;

/**
 * 桥接现有 EmbeddingProvider 到 LangChain4j EmbeddingModel。
 */
public class ProviderEmbeddingModel implements EmbeddingModel {

    private final EmbeddingProvider embeddingProvider;

    public ProviderEmbeddingModel(EmbeddingProvider embeddingProvider) {
        this.embeddingProvider = embeddingProvider;
    }

    @Override
    public Response<Embedding> embed(String text) {
        return Response.from(Embedding.from(embeddingProvider.embed(text)));
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
        List<String> texts = segments.stream().map(TextSegment::text).toList();
        List<Embedding> embeddings = embeddingProvider.embedBatch(texts).stream()
                .map(Embedding::from)
                .toList();
        return Response.from(embeddings);
    }

    @Override
    public int dimension() {
        return embeddingProvider.dimension();
    }
}
