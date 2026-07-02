package com.fast.knowledge.service;

import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.embedding.EmbeddingProvider;
import com.fast.knowledge.model.dto.SearchRequest;
import com.fast.knowledge.model.entity.KnowledgeBase;
import com.fast.knowledge.model.vo.SearchHitVO;
import com.fast.knowledge.vector.SearchHit;
import com.fast.knowledge.vector.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SearchService {

    private final KnowledgeBaseService knowledgeBaseService;
    private final EmbeddingProvider embeddingProvider;
    private final VectorStore vectorStore;
    private final SearchCacheService searchCacheService;

    public SearchService(KnowledgeBaseService knowledgeBaseService,
                         EmbeddingProvider embeddingProvider,
                         VectorStore vectorStore,
                         SearchCacheService searchCacheService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.embeddingProvider = embeddingProvider;
        this.vectorStore = vectorStore;
        this.searchCacheService = searchCacheService;
    }

    public List<SearchHitVO> search(SearchRequest request) throws Exception {
        if (request.getKbId() == null) {
            throw new BusinessException("请指定知识库");
        }
        if (request.getQuery() == null || request.getQuery().isBlank()) {
            throw new BusinessException("检索内容不能为空");
        }
        KnowledgeBase kb = knowledgeBaseService.getById(request.getKbId());
        int topK = request.getTopK() != null ? request.getTopK() : kb.getSearchTopK();
        double alpha = request.getAlpha() != null ? request.getAlpha() : kb.getSearchAlpha();

        var cached = searchCacheService.get(kb.getId(), request.getQuery(), topK, alpha);
        if (cached.isPresent()) {
            return cached.get();
        }

        float[] vector = embeddingProvider.embed(request.getQuery());
        List<SearchHit> hits = vectorStore.hybridSearch(kb.getId(), request.getQuery(), vector, topK, alpha);
        List<SearchHitVO> result = hits.stream().map(SearchHitVO::from).toList();

        searchCacheService.put(kb.getId(), request.getQuery(), topK, alpha, result);
        return result;
    }
}
