package com.fast.knowledge.service;

import com.fast.knowledge.model.vo.SearchHitVO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 混合检索融合 — 向量支路与关键词支路按 {@code kb.search_alpha} 加权求和。
 *
 * <p>两路分数已分别归一化到 [0,1]（向量=余弦，关键词=集合内最大相关度缩放），
 * 融合分保持同尺度，UI 相关度展示与 WP9 缺口采集阈值不受影响。
 * 单路无命中等价于退化为另一路（缺失路按 0 分参与加权）。
 */
public final class HybridFusion {

    private HybridFusion() {
    }

    public static List<SearchHitVO> fuse(List<SearchHitVO> vectorHits,
                                         List<SearchHitVO> keywordHits,
                                         Double alpha,
                                         int limit) {
        double a = alpha == null ? 0.6 : Math.max(0, Math.min(1, alpha));
        if (vectorHits == null || vectorHits.isEmpty()) {
            return keywordHits == null ? List.of() : keywordHits.subList(0, Math.min(limit, keywordHits.size()));
        }
        if (keywordHits == null || keywordHits.isEmpty()) {
            return vectorHits.subList(0, Math.min(limit, vectorHits.size()));
        }
        Map<Long, SearchHitVO> fused = new LinkedHashMap<>();
        for (SearchHitVO hit : vectorHits) {
            fused.put(hit.getChunkId(), clone(hit, hit.getScore() * a));
        }
        for (SearchHitVO hit : keywordHits) {
            SearchHitVO existing = fused.get(hit.getChunkId());
            if (existing != null) {
                existing.setScore(existing.getScore() + hit.getScore() * (1 - a));
            } else {
                fused.put(hit.getChunkId(), clone(hit, hit.getScore() * (1 - a)));
            }
        }
        List<SearchHitVO> result = new ArrayList<>(fused.values());
        result.sort((x, y) -> Double.compare(y.getScore(), x.getScore()));
        return result.subList(0, Math.min(limit, result.size()));
    }

    private static SearchHitVO clone(SearchHitVO source, double fusedScore) {
        SearchHitVO vo = new SearchHitVO();
        vo.setChunkId(source.getChunkId());
        vo.setDocumentId(source.getDocumentId());
        vo.setDocumentTitle(source.getDocumentTitle());
        vo.setDocType(source.getDocType());
        vo.setDocNo(source.getDocNo());
        vo.setSection(source.getSection());
        vo.setPageNo(source.getPageNo());
        vo.setAnchorType(source.getAnchorType());
        vo.setContent(source.getContent());
        vo.setScore(fusedScore);
        return vo;
    }
}
