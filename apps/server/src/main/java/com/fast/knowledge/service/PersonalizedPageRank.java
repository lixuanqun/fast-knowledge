package com.fast.knowledge.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * WP7.1 个性化 PageRank（HippoRAG 式扩散）— 替代固定 1 跳邻居扩展。
 *
 * <p>图按无向处理（kg_edge 的 src→dst 关系在检索扩展中语义对称），
 * 幂迭代：score'(v) = (1 - teleport) · Σ_{u∼v} score(u)/deg(u) + teleport · seed(v)。
 * 种子均匀分布初始质量，迭代固定轮数后按分数排序取 top 邻居。
 * 纯内存计算，数千边规模毫秒级，零外部依赖。
 */
public final class PersonalizedPageRank {

    private static final int DEFAULT_ITERATIONS = 30;

    private PersonalizedPageRank() {
    }

    /**
     * @param adjacency 无向邻接表（节点 → 相邻节点，重复边忽略）
     * @param seeds     种子实体（实体链接命中），至少一个
     * @param teleport  瞬移概率（0~1，越大越贴种子）
     * @return 全部节点按 PPR 分数降序（含种子，种子分数通常最高；调用方按需过滤）
     */
    public static Map<Long, Double> compute(Map<Long, List<Long>> adjacency, Set<Long> seeds,
                                            double teleport, int iterations) {
        Map<Long, Double> scores = new HashMap<>();
        if (adjacency == null || adjacency.isEmpty() || seeds == null || seeds.isEmpty()) {
            return scores;
        }
        double t = Math.max(0.0, Math.min(1.0, teleport));
        int rounds = Math.max(1, iterations <= 0 ? DEFAULT_ITERATIONS : iterations);

        Map<Long, Integer> degree = new HashMap<>();
        for (Map.Entry<Long, List<Long>> e : adjacency.entrySet()) {
            degree.merge(e.getKey(), e.getValue().size(), Integer::sum);
        }

        Map<Long, Double> current = new HashMap<>();
        double init = 1.0 / seeds.size();
        for (Long seed : seeds) {
            if (adjacency.containsKey(seed)) {
                current.put(seed, init);
            }
        }
        if (current.isEmpty()) {
            return scores;
        }

        for (int round = 0; round < rounds; round++) {
            Map<Long, Double> next = new HashMap<>();
            for (Long seed : seeds) {
                if (adjacency.containsKey(seed)) {
                    next.merge(seed, t * init, Double::sum);
                }
            }
            for (Map.Entry<Long, Double> e : current.entrySet()) {
                double share = e.getValue() * (1 - t);
                if (share == 0) {
                    continue;
                }
                List<Long> neighbors = adjacency.get(e.getKey());
                int deg = Math.max(1, degree.getOrDefault(e.getKey(), neighbors == null ? 0 : neighbors.size()));
                double per = share / deg;
                if (neighbors == null) {
                    continue;
                }
                for (Long n : neighbors) {
                    next.merge(n, per, Double::sum);
                }
            }
            current = next;
        }

        // 归一化到 0~1（按最大值），便于上层做权重缩放
        double max = current.values().stream().mapToDouble(Double::doubleValue).max().orElse(1);
        if (max <= 0) {
            return scores;
        }
        current.forEach((k, v) -> scores.put(k, v / max));
        return scores;
    }
}
