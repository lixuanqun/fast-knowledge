package com.fast.knowledge.service.impl;

import com.fast.knowledge.audit.AuditActions;
import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.common.StringUtils;
import com.fast.knowledge.langchain4j.KbEmbeddingStore;
import com.fast.knowledge.langchain4j.KbEmbeddingStoreFactory;
import com.fast.knowledge.langchain4j.SearchHitMapper;
import com.fast.knowledge.langchain4j.rerank.SearchRerankService;
import com.fast.knowledge.model.dto.SearchRequest;
import com.fast.knowledge.model.entity.KnowledgeBase;
import com.fast.knowledge.model.vo.SearchHitVO;
import com.fast.knowledge.service.AuditLogService;
import com.fast.knowledge.service.DocumentLifecycleFilter;
import com.fast.knowledge.service.DocumentRecallPolicy;
import com.fast.knowledge.service.KnowledgeBaseService;
import com.fast.knowledge.service.MetricsService;
import com.fast.knowledge.service.SearchCacheService;
import com.fast.knowledge.service.SearchService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SearchServiceImpl implements SearchService {

    private final KnowledgeBaseService knowledgeBaseService;
    private final EmbeddingModel embeddingModel;
    private final KbEmbeddingStoreFactory embeddingStoreFactory;
    private final SearchCacheService searchCacheService;
    private final SearchRerankService searchRerankService;
    private final DocumentLifecycleFilter documentLifecycleFilter;
    private final AuditLogService auditLogService;
    private final MetricsService metricsService;

    public SearchServiceImpl(KnowledgeBaseService knowledgeBaseService,
                             EmbeddingModel embeddingModel,
                             KbEmbeddingStoreFactory embeddingStoreFactory,
                             SearchCacheService searchCacheService,
                             SearchRerankService searchRerankService,
                             DocumentLifecycleFilter documentLifecycleFilter,
                             AuditLogService auditLogService,
                             MetricsService metricsService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.embeddingModel = embeddingModel;
        this.embeddingStoreFactory = embeddingStoreFactory;
        this.searchCacheService = searchCacheService;
        this.searchRerankService = searchRerankService;
        this.documentLifecycleFilter = documentLifecycleFilter;
        this.auditLogService = auditLogService;
        this.metricsService = metricsService;
    }

    @Override
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

        // Cache check (L1 + L2)
        var cached = searchCacheService.get(kb.getId(), request.getQuery(), topK, rerank, request.getDocType());
        if (cached.isPresent()) {
            List<SearchHitVO> hits = cached.get();
            metricsService.countSearch();
            metricsService.countSearchHits(hits.size());
            auditLogService.log(AuditActions.SEARCH, "KB", kb.getId(),
                    "query=" + StringUtils.truncate(request.getQuery(), 200)
                            + ", hits=" + hits.size() + ", cache=hit");
            return hits;
        }

        int baseFetch = rerank ? searchRerankService.candidateCount(topK) : topK;
        // 过取：生命周期过滤（禁用/未生效/已过期）后尽量仍能凑满 topK
        int fetchK = DocumentRecallPolicy.overFetch(baseFetch);

        // Full search pipeline with segmented timing
        List<SearchHitVO> hits = metricsService.timeSearch(() -> {
            // Segment 1: Embedding (with cache)
            Embedding queryEmbedding = metricsService.timeEmbedding(() -> {
                var cachedVec = searchCacheService.getEmbedding(request.getQuery());
                if (cachedVec.isPresent()) {
                    return Embedding.from(cachedVec.get());
                }
                Embedding emb = embeddingModel.embed(request.getQuery()).content();
                List<Float> vecList = emb.vectorAsList();
                float[] vec = new float[vecList.size()];
                for (int i = 0; i < vec.length; i++) {
                    vec[i] = vecList.get(i);
                }
                searchCacheService.putEmbedding(request.getQuery(), vec);
                return emb;
            });

            // Segment 2: Vector search
            List<SearchHitVO> rawHits = metricsService.timeVectorSearch(() -> {
                KbEmbeddingStore store = embeddingStoreFactory.getStore(kb.getId());
                EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .query(request.getQuery())
                        .maxResults(fetchK)
                        .build();
                EmbeddingSearchResult<TextSegment> result = store.search(searchRequest, request.getDocType());
                return result.matches().stream()
                        .map(match -> SearchHitMapper.fromMatch(match, kb.getId()))
                        .toList();
            });

            // Segment 2b: 排除禁用 / 未生效 / 已过期文档（Search / RAG / Chat 共用）
            List<SearchHitVO> eligible = documentLifecycleFilter.filter(rawHits);

            // Segment 3: Rerank (optional)
            if (rerank) {
                return metricsService.timeRerank(() ->
                        searchRerankService.rerank(request.getQuery(), eligible, topK));
            }
            return eligible.size() <= topK ? eligible : eligible.subList(0, topK);
        });

        metricsService.countSearch();
        metricsService.countSearchHits(hits.size());

        searchCacheService.put(kb.getId(), request.getQuery(), topK, rerank, request.getDocType(), hits);
        auditLogService.log(AuditActions.SEARCH, "KB", kb.getId(),
                "query=" + StringUtils.truncate(request.getQuery(), 200)
                        + ", hits=" + hits.size() + ", cache=miss");
        return hits;
    }
}
