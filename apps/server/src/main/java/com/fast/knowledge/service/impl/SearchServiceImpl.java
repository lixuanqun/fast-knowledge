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
import com.fast.knowledge.service.HybridFusion;
import com.fast.knowledge.service.KeywordSearchService;
import com.fast.knowledge.service.KnowledgeOpsService;
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
    private final KnowledgeOpsService knowledgeOpsService;
    private final DocumentLifecycleFilter documentLifecycleFilter;
    private final AuditLogService auditLogService;
    private final MetricsService metricsService;
    private final KeywordSearchService keywordSearchService;

    public SearchServiceImpl(KnowledgeBaseService knowledgeBaseService,
                             EmbeddingProvider embeddingProvider,
                             VectorSearchPort vectorSearchPort,
                             RerankPort rerankPort,
                             SearchCacheService searchCacheService,
                             KnowledgeOpsService knowledgeOpsService,
                             DocumentLifecycleFilter documentLifecycleFilter,
                             AuditLogService auditLogService,
                             MetricsService metricsService,
                             KeywordSearchService keywordSearchService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.embeddingProvider = embeddingProvider;
        this.vectorSearchPort = vectorSearchPort;
        this.rerankPort = rerankPort;
        this.searchCacheService = searchCacheService;
        this.knowledgeOpsService = knowledgeOpsService;
        this.documentLifecycleFilter = documentLifecycleFilter;
        this.auditLogService = auditLogService;
        this.metricsService = metricsService;
        this.keywordSearchService = keywordSearchService;
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

        // Segment 1: Embedding (with cache) — 提前计算供语义缓存近邻匹配与向量检索复用
        float[] queryVector = metricsService.timeEmbedding(() -> {
            var cachedVec = searchCacheService.getEmbedding(request.getQuery());
            if (cachedVec.isPresent()) {
                return cachedVec.get();
            }
            float[] vec = embeddingProvider.embed(request.getQuery());
            searchCacheService.putEmbedding(request.getQuery(), vec);
            return vec;
        });

        // Cache check: L1 + L2 精确 key，未命中走 WP4 语义缓存（向量近邻）
        var cached = searchCacheService.get(kb.getId(), request.getQuery(), topK, rerank, request.getDocType());
        if (cached.isEmpty()) {
            cached = searchCacheService.getSemantic(kb.getId(), queryVector, topK, rerank, request.getDocType());
        }
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
            // Segment 2: Vector search
            List<SearchHitVO> rawHits = metricsService.timeVectorSearch(() ->
                    vectorSearchPort.search(kb.getId(), queryVector, request.getQuery(), fetchK, request.getDocType()));

            // Segment 2a: Keyword search（FULLTEXT 支路，失败/关闭时为空，融合退化为纯向量）
            List<SearchHitVO> keywordHits = keywordSearchService.recall(
                    kb.getId(), request.getQuery(), request.getDocType(), fetchK);

            // Segment 2b: 加权融合（kb.search_alpha，0.6=偏向量）
            List<SearchHitVO> fused = HybridFusion.fuse(rawHits, keywordHits, kb.getSearchAlpha(), fetchK);

            // Segment 2c: 排除禁用 / 未生效 / 已过期文档（Search / RAG / Chat 共用）
            List<SearchHitVO> eligible = documentLifecycleFilter.filter(fused);

            // Segment 3: Rerank (optional)
            if (rerank) {
                return metricsService.timeRerank(() ->
                        rerankPort.rerank(request.getQuery(), eligible, topK));
            }
            return eligible.size() <= topK ? eligible : eligible.subList(0, topK);
        });

        metricsService.countSearch();
        metricsService.countSearchHits(hits.size());

        searchCacheService.putWithVector(kb.getId(), request.getQuery(), queryVector, topK, rerank, request.getDocType(), hits);
        // WP9：低命中或低分缺口采集（不阻塞主流程）
        double topScore = hits.isEmpty() ? 0 : hits.get(0).getScore();
        knowledgeOpsService.recordGap(kb.getId(), request.getQuery(), hits.size(), topScore);
        auditLogService.log(AuditActions.SEARCH, "KB", kb.getId(),
                "query=" + StringUtils.truncate(request.getQuery(), 200)
                        + ", hits=" + hits.size() + ", cache=miss");
        return hits;
    }
}
