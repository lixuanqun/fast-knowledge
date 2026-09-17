package com.fast.knowledge.ai.orchestration.retrieval;

import java.util.List;

/**
 * WP6 Agentic 检索步骤事件 — 供流式接口（Chat SSE）向端上透出检索进度。
 *
 * @param round     轮次（1 起）
 * @param mode      single=单轮 / agentic=分解多路 / refine=自评后补充检索
 * @param queries   本轮使用的查询（含子查询/改写查询）
 * @param totalHits 截至本轮累计的候选条数（去重后）
 */
public record RetrievalStep(int round, String mode, List<String> queries, int totalHits) {
}
