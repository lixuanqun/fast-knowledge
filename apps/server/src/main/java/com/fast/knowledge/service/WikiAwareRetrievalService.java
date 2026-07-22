package com.fast.knowledge.service;

import com.fast.knowledge.model.dto.SearchRequest;
import com.fast.knowledge.model.vo.SearchHitVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一召回入口（QA / Chat 共用）：
 * <ol>
 *   <li>复杂问法 → 轻量 Agentic 多跳（每跳仍走 Wiki+HYBRID 单轮）</li>
 *   <li>简单问法 → Wiki 优先 + HYBRID 兜底</li>
 * </ol>
 */
@Service
public class WikiAwareRetrievalService {

    private final SearchService searchService;
    private final WikiQueryRouter wikiQueryRouter;
    private final AgenticRetrievalService agenticRetrievalService;

    public WikiAwareRetrievalService(SearchService searchService,
                                     WikiQueryRouter wikiQueryRouter,
                                     AgenticRetrievalService agenticRetrievalService) {
        this.searchService = searchService;
        this.wikiQueryRouter = wikiQueryRouter;
        this.agenticRetrievalService = agenticRetrievalService;
    }

    public List<SearchHitVO> retrieve(Long kbId, String query) throws Exception {
        if (agenticRetrievalService.shouldUseAgentic(query)) {
            return agenticRetrievalService.retrieveMultiHop(kbId, query, this::retrieveOnce);
        }
        return retrieveOnce(kbId, query);
    }

    /** 单轮：Wiki 优先 + HYBRID（供多跳子查询调用，避免递归进 Agentic）。 */
    List<SearchHitVO> retrieveOnce(Long kbId, String query) throws Exception {
        List<SearchHitVO> wikiHits = wikiQueryRouter.resolveWikiHits(kbId, query);
        if (!wikiHits.isEmpty()) {
            SearchRequest request = new SearchRequest();
            request.setKbId(kbId);
            request.setQuery(query);
            List<SearchHitVO> hybrid = searchService.search(request);
            return merge(wikiHits, hybrid, 12);
        }
        SearchRequest request = new SearchRequest();
        request.setKbId(kbId);
        request.setQuery(query);
        return searchService.search(request);
    }

    private static List<SearchHitVO> merge(List<SearchHitVO> primary, List<SearchHitVO> secondary, int limit) {
        Map<String, SearchHitVO> map = new LinkedHashMap<>();
        for (SearchHitVO hit : primary) {
            map.putIfAbsent(key(hit), hit);
        }
        for (SearchHitVO hit : secondary) {
            map.putIfAbsent(key(hit), hit);
            if (map.size() >= limit) {
                break;
            }
        }
        return new ArrayList<>(map.values()).subList(0, Math.min(limit, map.size()));
    }

    private static String key(SearchHitVO hit) {
        if ("WIKI".equals(hit.getDocType())) {
            return "wiki:" + hit.getDocumentId();
        }
        return "doc:" + hit.getDocumentId() + ":" + hit.getChunkId();
    }
}
