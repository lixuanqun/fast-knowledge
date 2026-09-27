package com.fast.knowledge.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeGraphWp71Test {

    // ---- PersonalizedPageRank ----

    /** 线性图 A—B—C，种子 A：距离 1 的 B 高于距离 2 的 C；枢纽 B 承接双向流量得分最高（PPR 正确性质） */
    @Test
    void pprDecaysAlongMultiHops() {
        Map<Long, List<Long>> adj = Map.of(
                1L, List.of(2L),
                2L, List.of(1L, 3L),
                3L, List.of(2L));

        Map<Long, Double> ppr = PersonalizedPageRank.compute(adj, Set.of(1L), 0.15, 30);

        assertEquals(3, ppr.size());
        assertTrue(ppr.get(2L) > ppr.get(3L));
        assertTrue(ppr.get(1L) > ppr.get(3L));
        // 归一化后最大值为 1（本图中枢纽节点 B 承接 A/C 双向流量）
        assertEquals(1.0, ppr.get(2L), 1e-9);
    }

    /** 星形图 A—(B,C,D)，种子 A：三个邻居分数相同；种子 B 时邻居 A 分数最高（回走） */
    @Test
    void pprSpreadsUniformlyAndWalksBack() {
        Map<Long, List<Long>> star = Map.of(
                1L, List.of(2L, 3L, 4L),
                2L, List.of(1L),
                3L, List.of(1L),
                4L, List.of(1L));

        Map<Long, Double> fromA = PersonalizedPageRank.compute(star, Set.of(1L), 0.15, 30);
        assertEquals(fromA.get(2L), fromA.get(3L), 1e-9);
        assertEquals(fromA.get(3L), fromA.get(4L), 1e-9);

        Map<Long, Double> fromB = PersonalizedPageRank.compute(star, Set.of(2L), 0.15, 30);
        assertTrue(fromB.get(1L) > fromB.get(3L));
        assertTrue(fromB.get(1L) > fromB.get(4L));
    }

    /** 成为种子的节点获得瞬移质量：同一线图上 node3 作为种子时的分数高于非种子时 */
    @Test
    void seedNodeGainsTeleportMass() {
        Map<Long, List<Long>> line = Map.of(
                1L, List.of(2L),
                2L, List.of(1L, 3L),
                3L, List.of(2L));

        double node3NonSeed = PersonalizedPageRank.compute(line, Set.of(1L), 0.15, 30).get(3L);
        double node3AsSeed = PersonalizedPageRank.compute(line, Set.of(1L, 3L), 0.15, 30).get(3L);
        assertTrue(node3AsSeed > node3NonSeed);
    }

    /** 边界：种子不在图中 / 空图 → 空结果；teleport=1 退化为纯种子分布 */
    @Test
    void edgeCases() {
        assertTrue(PersonalizedPageRank.compute(Map.of(), Set.of(1L), 0.15, 30).isEmpty());
        assertTrue(PersonalizedPageRank.compute(Map.of(1L, List.of(2L)), Set.of(9L), 0.15, 30).isEmpty());

        Map<Long, List<Long>> adj = Map.of(1L, List.of(2L), 2L, List.of(1L));
        Map<Long, Double> pure = PersonalizedPageRank.compute(adj, Set.of(1L), 1.0, 5);
        assertEquals(1.0, pure.get(1L), 1e-9);
        assertEquals(0.0, pure.getOrDefault(2L, 0.0), 1e-9);
    }

    // ---- parseKeywordJson ----

    @Test
    void parsesValidKeywordJson() {
        var pair = KnowledgeGraphService.parseKeywordJson(
                "前置说明 {\"low\":[\"公务用车\",\"维修\"],\"high\":[\"车辆管理\"]} 后缀");
        assertNotNull(pair);
        assertEquals(List.of("公务用车", "维修"), pair.low());
        assertEquals(List.of("车辆管理"), pair.high());
    }

    @Test
    void rejectsInvalidOrEmptyKeywordJson() {
        assertNull(KnowledgeGraphService.parseKeywordJson(null));
        assertNull(KnowledgeGraphService.parseKeywordJson("没有 JSON"));
        assertNull(KnowledgeGraphService.parseKeywordJson("{\"low\":[],\"high\":[]}"));
        assertNull(KnowledgeGraphService.parseKeywordJson("不是 json 的普通回答"));
    }

    /** 超长词截断到 32 字、每组最多 4 个（单词 ≥2 字才收录） */
    @Test
    void normalizesKeywords() {
        String longWord = "很".repeat(40);
        var pair = KnowledgeGraphService.parseKeywordJson(
                "{\"low\":[\"" + longWord + "\",\"运维\",\"车辆\",\"台账\",\"考核\",\"巡查\"],\"high\":[]}");
        assertNotNull(pair);
        assertEquals(4, pair.low().size());
        assertEquals(32, pair.low().get(0).length());
    }
}
