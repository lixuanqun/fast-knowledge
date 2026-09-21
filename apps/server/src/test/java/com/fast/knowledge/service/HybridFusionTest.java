package com.fast.knowledge.service;

import com.fast.knowledge.model.vo.SearchHitVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HybridFusionTest {

    private SearchHitVO hit(long chunkId, double score) {
        SearchHitVO vo = new SearchHitVO();
        vo.setChunkId(chunkId);
        vo.setScore(score);
        return vo;
    }

    @Test
    void emptyKeywordLegReturnsVectorHits() {
        var fused = HybridFusion.fuse(List.of(hit(1, 0.9), hit(2, 0.5)), List.of(), 0.6, 8);

        assertEquals(2, fused.size());
        assertEquals(0.9, fused.get(0).getScore());
    }

    @Test
    void emptyVectorLegReturnsKeywordHits() {
        var fused = HybridFusion.fuse(List.of(), List.of(hit(1, 1.0), hit(2, 0.4)), 0.6, 8);

        assertEquals(2, fused.size());
        assertEquals(1.0, fused.get(0).getScore());
    }

    @Test
    void bothLegsBlendWithAlpha() {
        var vector = List.of(hit(1, 0.8), hit(2, 0.6));
        var keyword = List.of(hit(2, 1.0), hit(3, 0.5));

        var fused = HybridFusion.fuse(vector, keyword, 0.5, 8);

        assertEquals(3, fused.size());
        // chunk2 双路命中：0.5*0.6 + 0.5*1.0 = 0.8，应排第一
        assertEquals(2L, fused.get(0).getChunkId());
        assertEquals(0.8, fused.get(0).getScore(), 1e-9);
        // chunk1 仅向量：0.5*0.8 = 0.4；chunk3 仅关键词：0.5*0.5 = 0.25
        assertEquals(1L, fused.get(1).getChunkId());
        assertEquals(0.25, fused.get(2).getScore(), 1e-9);
    }

    @Test
    void resultIsSortedByFusedScoreAndLimited() {
        var vector = List.of(hit(1, 0.95), hit(2, 0.9));
        var keyword = List.of(hit(3, 1.0));

        var fused = HybridFusion.fuse(vector, keyword, 0.6, 2);

        assertEquals(2, fused.size());
        assertTrue(fused.get(0).getScore() >= fused.get(1).getScore());
        assertEquals(1L, fused.get(0).getChunkId());
    }

    @Test
    void nullAndOutOfRangeAlphaAreClamped() {
        var vector = List.of(hit(1, 0.8));
        var keyword = List.of(hit(1, 0.4));

        // null → 默认 0.6：0.6*0.8 + 0.4*0.4 = 0.64
        assertEquals(0.64, HybridFusion.fuse(vector, keyword, null, 8).get(0).getScore(), 1e-9);
        // 越界钳位：2.0 → 1.0（纯向量 0.8）；-1.0 → 0.0（纯关键词 0.4）
        assertEquals(0.8, HybridFusion.fuse(vector, keyword, 2.0, 8).get(0).getScore(), 1e-9);
        assertEquals(0.4, HybridFusion.fuse(vector, keyword, -1.0, 8).get(0).getScore(), 1e-9);
    }
}
