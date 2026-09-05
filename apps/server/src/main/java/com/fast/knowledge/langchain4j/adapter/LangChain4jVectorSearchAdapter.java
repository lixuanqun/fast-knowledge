package com.fast.knowledge.langchain4j.adapter;

import com.fast.knowledge.ai.port.VectorSearchPort;
import com.fast.knowledge.langchain4j.KbEmbeddingStore;
import com.fast.knowledge.langchain4j.KbEmbeddingStoreFactory;
import com.fast.knowledge.langchain4j.SearchHitMapper;
import com.fast.knowledge.model.vo.SearchHitVO;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LangChain4jVectorSearchAdapter implements VectorSearchPort {

    private final KbEmbeddingStoreFactory embeddingStoreFactory;

    public LangChain4jVectorSearchAdapter(KbEmbeddingStoreFactory embeddingStoreFactory) {
        this.embeddingStoreFactory = embeddingStoreFactory;
    }

    @Override
    public List<SearchHitVO> search(Long kbId, float[] queryVector, String query, int fetchK, String docType) {
        KbEmbeddingStore store = embeddingStoreFactory.getStore(kbId);
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(Embedding.from(queryVector))
                .query(query)
                .maxResults(fetchK)
                .build();
        EmbeddingSearchResult<TextSegment> result = store.search(searchRequest, docType);
        return result.matches().stream()
                .map(match -> SearchHitMapper.fromMatch(match, kbId))
                .toList();
    }
}
