package com.fast.knowledge.service.impl;

import com.fast.knowledge.ai.port.RerankPort;
import com.fast.knowledge.ai.port.VectorSearchPort;
import com.fast.knowledge.audit.AuditActions;
import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.common.StringUtils;
import com.fast.knowledge.embedding.EmbeddingProvider;
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
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SearchServiceImpl implements SearchService {

    private final KnowledgeBaseService knowledgeBaseService;
    private final EmbeddingProvider embeddingProvider;
    private final VectorSearchPort vectorSearchPort;
    private final RerankPort rerankPort;
    private final SearchCacheService searchCacheService;
    private final DocumentLifecycleFilter documentLifecycleFilter;
    private final AuditLogService auditLogService;
    private final MetricsService metricsService;

    public SearchServiceImpl(KnowledgeBaseService knowledgeBaseService,
                             EmbeddingProvider embeddingProvider,
                             VectorSearchPort vectorSearchPort,
                             RerankPort rerankPort,
                             SearchCacheService searchCacheService,
                             DocumentLifecycleFilter documentLifecycleFilter,
                             AuditLogService auditLogService,
                             MetricsService metricsService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.embeddingProvider = embeddingProvider;
        this.vectorSearchPort = vectorSearchPort;
        this.rerankPort = rerankPort;
        this.searchCacheService = searchCacheService;
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
        boolean rerank = rerankPort.isActive();

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

        int baseFetch = rerank ? rerankPort.candidateCount(topK) : topK;
        // 过取：生命周期过滤（禁用/未生效/已过期）后尽量仍能凑满 topK
        int fetchK = DocumentRecallPolicy.overFetch(baseFetch);

        // Full search pipeline with segmented timing
        List<SearchHitVO> hits = metricsService.timeSearch(() -> {
            // Segment 1: Embedding (with cache)
            float[] queryVector = metricsService.timeEmbedding(() -> {
                var cachedVec = searchCacheService.getEmbedding(request.getQuery());
                if (cachedVec.isPresent()) {
                    return cachedVec.get();
                }
                float[] vec = embeddingProvider.embed(request.getQuery());
                searchCacheService.putEmbedding(request.getQuery(), vec);
                return vec;
            });

            // Segment 2: Vector search
            List<SearchHitVO> rawHits = metricsService.timeVectorSearch(() ->
                    vectorSearchPort.search(kb.getId(), queryVector, request.getQuery(), fetchK, request.getDocType()));

            // Segment 2b: 排除禁用 / 未生效 / 已过期文档（Search / RAG / Chat 共用）
            List<SearchHitVO> eligible = documentLifecycleFilter.filter(rawHits);

            // Segment 3: Rerank (optional)
            if (rerank) {
                return metricsService.timeRerank(() ->
                        rerankPort.rerank(request.getQuery(), eligible, topK));
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
