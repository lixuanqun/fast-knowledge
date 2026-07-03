package com.fast.knowledge.service;

import com.fast.knowledge.audit.AuditActions;
import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.langchain4j.KbEmbeddingStore;
import com.fast.knowledge.langchain4j.KbEmbeddingStoreFactory;
import com.fast.knowledge.langchain4j.SearchHitMapper;
import com.fast.knowledge.langchain4j.rerank.SearchRerankService;
import com.fast.knowledge.model.dto.SearchRequest;
import com.fast.knowledge.model.entity.KnowledgeBase;
import com.fast.knowledge.model.vo.SearchHitVO;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SearchService {

    private final KnowledgeBaseService knowledgeBaseService;
    private final EmbeddingModel embeddingModel;
    private final KbEmbeddingStoreFactory embeddingStoreFactory;
    private final SearchCacheService searchCacheService;
    private final SearchRerankService searchRerankService;
    private final AuditLogService auditLogService;

    public SearchService(KnowledgeBaseService knowledgeBaseService,
                         EmbeddingModel embeddingModel,
                         KbEmbeddingStoreFactory embeddingStoreFactory,
                         SearchCacheService searchCacheService,
                         SearchRerankService searchRerankService,
                         AuditLogService auditLogService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.embeddingModel = embeddingModel;
        this.embeddingStoreFactory = embeddingStoreFactory;
        this.searchCacheService = searchCacheService;
        this.searchRerankService = searchRerankService;
        this.auditLogService = auditLogService;
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
        boolean rerank = searchRerankService.isActive();

        var cached = searchCacheService.get(kb.getId(), request.getQuery(), topK, rerank, request.getDocType());
        if (cached.isPresent()) {
            return cached.get();
        }

        int fetchK = rerank ? searchRerankService.candidateCount(topK) : topK;
        Embedding queryEmbedding = embeddingModel.embed(request.getQuery()).content();
        KbEmbeddingStore store = embeddingStoreFactory.getStore(kb.getId());
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .query(request.getQuery())
                .maxResults(fetchK)
                .build();
        EmbeddingSearchResult<TextSegment> result = store.search(searchRequest, request.getDocType());
        List<SearchHitVO> hits = result.matches().stream()
                .map(match -> SearchHitMapper.fromMatch(match, kb.getId()))
                .toList();
        if (rerank) {
            hits = searchRerankService.rerank(request.getQuery(), hits, topK);
        }

        searchCacheService.put(kb.getId(), request.getQuery(), topK, rerank, request.getDocType(), hits);
        auditLogService.log(AuditActions.SEARCH, "KB", kb.getId(),
                "query=" + truncate(request.getQuery(), 200) + ", hits=" + hits.size());
        return hits;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
