package com.fast.knowledge.ai.orchestration.retrieval;

import com.fast.knowledge.ai.port.ChatPort;
import com.fast.knowledge.common.StringUtils;
import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.service.MetricsService;
import com.fast.knowledge.service.QueryComplexityClassifier;
import com.fast.knowledge.model.vo.SearchHitVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 轻量 Agentic 多跳：复杂问法拆成 ≤N 个子查询，分别单轮召回后合并。
 * 不做可视化工作流；失败时回退原查询单次检索。
 */
@Slf4j
@Service
public class AgenticRetrievalService {

    private static final String DECOMPOSE_PROMPT = """
            你是检索规划助手。将用户的复杂问题拆成 2～3 个可独立检索的短查询。
            规则：
            1. 只输出 JSON 字符串数组，例如 ["查询1","查询2"]
            2. 每个查询应具体、可检索，不要解释
            3. 不要发明用户未提及的实体
            4. 子查询数量不超过 3 个""";

    private static final Pattern JSON_ARRAY = Pattern.compile("\\[.*]", Pattern.DOTALL);
    private static final Pattern JSON_OBJECT = Pattern.compile("\\{.*}", Pattern.DOTALL);

    private static final String SUFFICIENCY_PROMPT = """
            你是检索质量评估器。给定用户问题与已召回片段的摘要，判断这些片段是否足以完整、准确地回答问题。
            规则：
            1. 只输出 JSON 对象：{"sufficient": true|false, "missing": ["缺失要点", ...]}
            2. missing 列出回答问题还缺的具体信息点（最多 3 个；足够时为空数组）
            3. 宁可判充分也不要臆造缺失点；片段间信息互补即可判充分""";

    /** 充分性自评结果 */
    record Sufficiency(boolean sufficient, List<String> missing) {
    }

    private final KnowledgeProperties properties;
    private final ChatPort chatPort;
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;

    public AgenticRetrievalService(KnowledgeProperties properties,
                                   ChatPort chatPort,
                                   ObjectMapper objectMapper,
                                   MetricsService metricsService) {
        this.properties = properties;
        this.chatPort = chatPort;
        this.objectMapper = objectMapper;
        this.metricsService = metricsService;
    }

    public boolean shouldUseAgentic(String query) {
        return properties.getAgentic().isEnabled() && QueryComplexityClassifier.isComplex(query);
    }

    /**
     * @param singlePass 单轮召回（不得再回调本服务，避免递归）
     */
    public List<SearchHitVO> retrieveMultiHop(Long kbId,
                                              String query,
                                              BiFunctionThrowing<Long, String, List<SearchHitVO>> singlePass)
            throws Exception {
        return retrieveMultiHop(kbId, query, singlePass, null);
    }

    /**
     * WP6 完整闭环：分解多路召回 → LLM 自评充分性 → 不充分则构造补充查询再检索（≤ maxRounds 轮）。
     *
     * @param steps 检索步骤回调（可为 null），供流式接口透出进度
     */
    public List<SearchHitVO> retrieveMultiHop(Long kbId,
                                              String query,
                                              BiFunctionThrowing<Long, String, List<SearchHitVO>> singlePass,
                                              Consumer<RetrievalStep> steps)
            throws Exception {
        int maxRounds = Math.max(1, properties.getAgentic().getMaxRounds());

        List<String> queries = planSubQueries(query);
        Map<String, SearchHitVO> merged = collect(kbId, queries, singlePass);
        metricsService.countAgentic(queries.size());
        notifyStep(steps, 1, "agentic", queries, merged.size());

        // 自评 → 补充检索循环（round 2..maxRounds）
        for (int round = 2; round <= maxRounds; round++) {
            if (!properties.getAgentic().isSelfCritique() || merged.isEmpty()) {
                break;
            }
            Sufficiency s = evaluateSufficiency(query, new ArrayList<>(merged.values()));
            if (s.sufficient()) {
                break;
            }
            String refine = buildRefineQuery(query, s.missing());
            if (refine == null || queries.contains(refine)) {
                break;
            }
            List<String> refineQueries = List.of(refine);
            merged = collectInto(merged, kbId, refineQueries, singlePass);
            notifyStep(steps, round, "refine", refineQueries, merged.size());
            queries = refineQueries;
            metricsService.countAgentic(1);
        }

        if (merged.isEmpty()) {
            return singlePass.apply(kbId, query);
        }
        return new ArrayList<>(merged.values()).subList(0, Math.min(12, merged.size()));
    }

    private Map<String, SearchHitVO> collect(Long kbId, List<String> queries,
                                             BiFunctionThrowing<Long, String, List<SearchHitVO>> singlePass)
            throws Exception {
        return collectInto(new LinkedHashMap<>(), kbId, queries, singlePass);
    }

    private Map<String, SearchHitVO> collectInto(Map<String, SearchHitVO> merged, Long kbId, List<String> queries,
                                                 BiFunctionThrowing<Long, String, List<SearchHitVO>> singlePass)
            throws Exception {
        for (String sub : queries) {
            List<SearchHitVO> hits = singlePass.apply(kbId, sub);
            if (hits == null) {
                continue;
            }
            for (SearchHitVO hit : hits) {
                merged.putIfAbsent(StringUtils.dedupeKey(hit.getDocType(), hit.getDocumentId(), hit.getChunkId()), hit);
            }
            if (merged.size() >= 16) {
                return merged;
            }
        }
        return merged;
    }

    private void notifyStep(Consumer<RetrievalStep> steps, int round, String mode,
                            List<String> queries, int totalHits) {
        if (steps != null) {
            try {
                steps.accept(new RetrievalStep(round, mode, queries, totalHits));
            } catch (Exception e) {
                log.debug("检索步骤回调失败: {}", e.getMessage());
            }
        }
    }

    /** LLM 自评召回是否充分；评估失败按充分处理（不重检，保守省成本） */
    Sufficiency evaluateSufficiency(String query, List<SearchHitVO> hits) {
        try {
            StringBuilder userPrompt = new StringBuilder("用户问题：").append(query).append("\n\n已召回片段摘要：\n");
            int i = 1;
            for (SearchHitVO hit : hits) {
                String content = hit.getContent() == null ? "" : hit.getContent();
                userPrompt.append("【片段 ").append(i++).append("】")
                        .append(content, 0, Math.min(content.length(), 160)).append("\n");
                if (i > 6) {
                    break;
                }
            }
            userPrompt.append("\n请输出 JSON 对象：");
            String raw = chatPort.complete(SUFFICIENCY_PROMPT, userPrompt.toString());
            if (raw == null || raw.isBlank()) {
                return new Sufficiency(true, List.of());
            }
            Matcher m = JSON_OBJECT.matcher(raw.trim());
            if (!m.find()) {
                return new Sufficiency(true, List.of());
            }
            Map<String, Object> parsed = objectMapper.readValue(m.group(),
                    new TypeReference<Map<String, Object>>() {
                    });
            boolean sufficient = Boolean.TRUE.equals(parsed.get("sufficient"))
                    || "true".equalsIgnoreCase(String.valueOf(parsed.get("sufficient")));
            List<String> missing = new ArrayList<>();
            Object rawMissing = parsed.get("missing");
            if (rawMissing instanceof List<?> list) {
                for (Object o : list) {
                    if (o != null && !String.valueOf(o).isBlank()) {
                        missing.add(String.valueOf(o));
                    }
                }
            }
            log.info("Agentic 自评: sufficient={} missing={}", sufficient, missing);
            return new Sufficiency(sufficient, missing);
        } catch (Exception e) {
            log.debug("Agentic 自评失败（按充分处理）: {}", e.getMessage());
            return new Sufficiency(true, List.of());
        }
    }

    /** 用缺失要点构造补充查询；无可用要点返回 null */
    String buildRefineQuery(String query, List<String> missing) {
        if (missing == null || missing.isEmpty()) {
            return null;
        }
        String joined = String.join(" ", missing).trim();
        return joined.isBlank() ? null : query + " " + joined;
    }

    List<String> planSubQueries(String query) {
        int max = Math.max(2, Math.min(properties.getAgentic().getMaxSubQueries(), 4));
        Set<String> planned = new LinkedHashSet<>();
        planned.add(query.trim());

        if (properties.getAgentic().isLlmDecompose()) {
            try {
                for (String s : llmDecompose(query, max)) {
                    if (s != null && !s.isBlank()) {
                        planned.add(s.trim());
                    }
                    if (planned.size() >= max) {
                        break;
                    }
                }
            } catch (Exception e) {
                log.debug("Agentic LLM decompose failed, fallback heuristic: {}", e.getMessage());
            }
        }

        if (planned.size() < 2) {
            for (String s : heuristicSplit(query)) {
                planned.add(s);
                if (planned.size() >= max) {
                    break;
                }
            }
        }

        List<String> list = new ArrayList<>(planned);
        return list.size() > max ? list.subList(0, max) : list;
    }

    private List<String> llmDecompose(String query, int max) throws Exception {
        String raw = chatPort.complete(DECOMPOSE_PROMPT, "用户问题：" + query + "\nJSON数组：");
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        Matcher m = JSON_ARRAY.matcher(raw.trim());
        String json = m.find() ? m.group() : raw.trim();
        List<String> parsed = objectMapper.readValue(json, new TypeReference<>() {
        });
        return parsed.stream()
                .filter(s -> s != null && !s.isBlank())
                .limit(max)
                .toList();
    }

    public static List<String> heuristicSplit(String query) {
        String q = query.trim();
        // 「A和B的区别」类
        String[] parts = q.split("(?:对比|比较|区别|差异|以及|还有|同时|分别是|分别)");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            String t = p.replaceAll("^[的与和与\\s，,：:]+|[的与和与\\s，,：:]+$", "").trim();
            if (t.length() >= 4) {
                out.add(t);
            }
        }
        if (out.size() >= 2) {
            return out;
        }
        // 「A与B」
        String[] andParts = q.split("[与和]");
        out.clear();
        for (String p : andParts) {
            String t = p.trim();
            if (t.length() >= 4 && t.length() < q.length()) {
                out.add(t);
            }
        }
        return out;
    }



    @FunctionalInterface
    public interface BiFunctionThrowing<A, B, R> {
        R apply(A a, B b) throws Exception;
    }
}
