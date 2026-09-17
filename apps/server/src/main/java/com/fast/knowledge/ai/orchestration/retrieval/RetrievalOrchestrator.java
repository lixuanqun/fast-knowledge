package com.fast.knowledge.ai.orchestration.retrieval;

import com.fast.knowledge.common.StringUtils;
import com.fast.knowledge.model.dto.SearchRequest;
import com.fast.knowledge.service.SearchService;
import com.fast.knowledge.model.vo.SearchHitVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 检索编排器（Retrieval Orchestrator，QA / Chat 共用统一召回入口）：
 * <ol>
 *   <li>复杂问法 → 轻量 Agentic 多跳（每跳仍走 Wiki+HYBRID 单轮）</li>
 *   <li>简单问法 → Wiki 优先 + HYBRID 兜底</li>
 * </ol>
 */
@Service
public class RetrievalOrchestrator {

    private final SearchService searchService;
    private final WikiQueryRouter wikiQueryRouter;
    private final AgenticRetrievalService agenticRetrievalService;
    private final com.fast.knowledge.service.KnowledgeGraphService knowledgeGraphService;

    public RetrievalOrchestrator(SearchService searchService,
                                     WikiQueryRouter wikiQueryRouter,
                                     AgenticRetrievalService agenticRetrievalService,
                                     com.fast.knowledge.service.KnowledgeGraphService knowledgeGraphService) {
        this.searchService = searchService;
        this.wikiQueryRouter = wikiQueryRouter;
        this.agenticRetrievalService = agenticRetrievalService;
        this.knowledgeGraphService = knowledgeGraphService;
    }

    public List<SearchHitVO> retrieve(Long kbId, String query) throws Exception {
        return retrieve(kbId, query, null);
    }

    /** WP6：带检索步骤回调的重载（流式接口透出进度用；可为 null） */
    public List<SearchHitVO> retrieve(Long kbId, String query, java.util.function.Consumer<RetrievalStep> steps) throws Exception {
        if (agenticRetrievalService.shouldUseAgentic(query)) {
            return agenticRetrievalService.retrieveMultiHop(kbId, query, this::retrieveOnce, steps);
        }
        List<SearchHitVO> hits = retrieveOnce(kbId, query);
        if (steps != null) {
            try {
                steps.accept(new RetrievalStep(1, "single", List.of(query), hits.size()));
            } catch (Exception ignored) {
            }
        }
        return hits;
    }

    /** 单轮：Wiki 优先 + HYBRID（供多跳子查询调用，避免递归进 Agentic）。 */
    List<SearchHitVO> retrieveOnce(Long kbId, String query) throws Exception {
        List<SearchHitVO> wikiHits = wikiQueryRouter.resolveWikiHits(kbId, query);
        if (!wikiHits.isEmpty()) {
            SearchRequest request = new SearchRequest();
            request.setKbId(kbId);
            request.setQuery(query);
            List<SearchHitVO> hybrid = searchService.search(request);
            return expandWithGraph(kbId, query, merge(wikiHits, hybrid, 12));
        }
        SearchRequest request = new SearchRequest();
        request.setKbId(kbId);
        request.setQuery(query);
        return expandWithGraph(kbId, query, searchService.search(request));
    }

    /** WP7：KG 实体链接 + 1 跳邻居证据 chunk 扩展召回（低权重，去重交给 merge） */
    private List<SearchHitVO> expandWithGraph(Long kbId, String query, List<SearchHitVO> hits) {
        if (!knowledgeGraphService.isEnabled() || hits == null || hits.isEmpty()) {
            return hits;
        }
        try {
            List<SearchHitVO> kgExt = knowledgeGraphService.expandForQuery(kbId, query, 8);
            if (kgExt.isEmpty()) {
                return hits;
            }
            return merge(hits, kgExt, hits.size() + kgExt.size());
        } catch (Exception e) {
            return hits;
        }
    }

    private static List<SearchHitVO> merge(List<SearchHitVO> primary, List<SearchHitVO> secondary, int limit) {
        Map<String, SearchHitVO> map = new LinkedHashMap<>();
        for (SearchHitVO hit : primary) {
            map.putIfAbsent(StringUtils.dedupeKey(hit.getDocType(), hit.getDocumentId(), hit.getChunkId()), hit);
        }
        for (SearchHitVO hit : secondary) {
            map.putIfAbsent(StringUtils.dedupeKey(hit.getDocType(), hit.getDocumentId(), hit.getChunkId()), hit);
            if (map.size() >= limit) {
                break;
            }
        }
        return new ArrayList<>(map.values()).subList(0, Math.min(limit, map.size()));
    }


}
