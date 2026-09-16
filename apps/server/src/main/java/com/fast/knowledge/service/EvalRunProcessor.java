package com.fast.knowledge.service;

import com.fast.knowledge.mapper.EvalRunItemMapper;
import com.fast.knowledge.mapper.EvalRunMapper;
import com.fast.knowledge.model.dto.SearchRequest;
import com.fast.knowledge.model.entity.EvalCase;
import com.fast.knowledge.model.entity.EvalDataset;
import com.fast.knowledge.model.entity.EvalRun;
import com.fast.knowledge.model.entity.EvalRunItem;
import com.fast.knowledge.model.vo.SearchHitVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 评测运行异步处理器 — 独立 Bean 确保 {@code @Async} AOP 代理生效。
 * 指标：recall@k（期望 chunk 命中比例）、MRR（首个期望 chunk 名次倒数均值）、
 * 关键词命中率、命中率、平均延迟。LLM 忠实度 judge 待配置真实 LLM 后启用。
 */
@Slf4j
@Component
public class EvalRunProcessor {

    private final SearchService searchService;
    private final EvalRunMapper evalRunMapper;
    private final EvalRunItemMapper evalRunItemMapper;
    private final ObjectMapper objectMapper;

    /** Classic 形态单实例部署，JVM 内防重入即可 */
    private final AtomicBoolean running = new AtomicBoolean(false);

    public EvalRunProcessor(SearchService searchService, EvalRunMapper evalRunMapper,
                            EvalRunItemMapper evalRunItemMapper, ObjectMapper objectMapper) {
        this.searchService = searchService;
        this.evalRunMapper = evalRunMapper;
        this.evalRunItemMapper = evalRunItemMapper;
        this.objectMapper = objectMapper;
    }

    public boolean isRunning() {
        return running.get();
    }

    @Async("evalExecutor")
    public void execute(Runnable evaluation, Long runId) {
        if (!running.compareAndSet(false, true)) {
            markFailed(runId, "已有评测运行进行中，请稍后再试");
            return;
        }
        try {
            evaluation.run();
        } catch (Exception e) {
            log.error("评测运行 {} 执行失败", runId, e);
            markFailed(runId, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        } finally {
            running.set(false);
        }
    }

    /** 由调用方以 {@code UserContext.wrap(...)} 包装后传入，保证检索权限校验可用 */
    public void runEvaluation(Long runId, EvalDataset dataset, List<EvalCase> cases) {
        doRun(runId, dataset, cases);
    }

    private void doRun(Long runId, EvalDataset dataset, List<EvalCase> cases) {
        List<Double> recalls = new ArrayList<>();
        List<Double> reciprocalRanks = new ArrayList<>();
        List<Integer> keywordHits = new ArrayList<>();
        long latencySum = 0;
        int hitCases = 0;
        int chunkExpectationCases = 0;

        for (EvalCase evalCase : cases) {
            long startAt = System.currentTimeMillis();
            List<Long> hitChunkIds = new ArrayList<>();
            Integer firstHitRank = null;
            Double recall = null;
            Integer keywordHit = null;
            String error = null;

            try {
                SearchRequest request = new SearchRequest();
                request.setKbId(dataset.getKbId());
                request.setQuery(evalCase.getQuestion());
                request.setTopK(dataset.getTopK());
                List<SearchHitVO> hits = searchService.search(request);
                hits.forEach(h -> hitChunkIds.add(h.getChunkId()));

                List<Long> expected = parseLongs(evalCase.getExpectedChunkIds());
                if (!expected.isEmpty()) {
                    chunkExpectationCases++;
                    for (int i = 0; i < hits.size() && firstHitRank == null; i++) {
                        if (expected.contains(hits.get(i).getChunkId())) {
                            firstHitRank = i + 1;
                        }
                    }
                    long matched = hits.stream().map(SearchHitVO::getChunkId).filter(expected::contains).distinct().count();
                    recall = (double) matched / expected.size();
                    recalls.add(recall);
                    if (firstHitRank != null) {
                        reciprocalRanks.add(1.0 / firstHitRank);
                        hitCases++;
                    }
                }

                List<String> keywords = parseStrings(evalCase.getExpectedKeywords());
                if (!keywords.isEmpty()) {
                    String content = String.join("\n", hits.stream().map(SearchHitVO::getContent).toList());
                    // 忽略空白差异，避免"30分钟" vs "30 分钟"误判
                    String compact = content.replaceAll("\\s+", "");
                    keywordHit = keywords.stream().allMatch(k -> compact.contains(k.replaceAll("\\s+", ""))) ? 1 : 0;
                    keywordHits.add(keywordHit);
                }
            } catch (Exception e) {
                log.warn("评测用例 {} 执行失败: {}", evalCase.getId(), e.getMessage());
                error = e.getMessage();
            }

            int latencyMs = (int) Math.min(System.currentTimeMillis() - startAt, Integer.MAX_VALUE);
            latencySum += latencyMs;

            EvalRunItem item = new EvalRunItem();
            item.setRunId(runId);
            item.setCaseId(evalCase.getId());
            item.setQuestion(evalCase.getQuestion());
            item.setFirstHitRank(firstHitRank);
            item.setRecall(recall);
            item.setKeywordHit(keywordHit);
            item.setLatencyMs(latencyMs);
            item.setHitChunkIds(toJson(hitChunkIds));
            evalRunItemMapper.insert(item);
            if (error != null) {
                log.warn("评测用例 {} 结果不完整（检索异常）", evalCase.getId());
            }
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("total_cases", cases.size());
        metrics.put("recall_at_k", avg(recalls));
        metrics.put("mrr", avg(reciprocalRanks));
        metrics.put("hit_rate", chunkExpectationCases == 0 ? null : (double) hitCases / chunkExpectationCases);
        metrics.put("keyword_hit_rate", avgInt(keywordHits));
        metrics.put("avg_latency_ms", cases.isEmpty() ? 0 : latencySum / cases.size());

        EvalRun run = new EvalRun();
        run.setId(runId);
        run.setStatus("DONE");
        run.setMetricsJson(toJson(metrics));
        run.setFinishedAt(LocalDateTime.now());
        evalRunMapper.updateById(run);
    }

    private void markFailed(Long runId, String message) {
        EvalRun run = new EvalRun();
        run.setId(runId);
        run.setStatus("FAILED");
        run.setError(message);
        run.setFinishedAt(LocalDateTime.now());
        evalRunMapper.updateById(run);
    }

    private List<Long> parseLongs(String json) {
        try {
            return json == null || json.isBlank()
                    ? List.of()
                    : objectMapper.readValue(json, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String> parseStrings(String json) {
        try {
            return json == null || json.isBlank()
                    ? List.of()
                    : objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private Double avg(List<Double> values) {
        return values.isEmpty() ? null : values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private Double avgInt(List<Integer> values) {
        return values.isEmpty() ? null : values.stream().mapToDouble(Integer::doubleValue).average().orElse(0);
    }
}
