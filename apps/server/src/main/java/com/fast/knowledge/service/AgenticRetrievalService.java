package com.fast.knowledge.service;

import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.llm.LlmModelRegistry;
import com.fast.knowledge.model.vo.SearchHitVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    private final KnowledgeProperties properties;
    private final LlmModelRegistry llmModelRegistry;
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;

    public AgenticRetrievalService(KnowledgeProperties properties,
                                   LlmModelRegistry llmModelRegistry,
                                   ObjectMapper objectMapper,
                                   MetricsService metricsService) {
        this.properties = properties;
        this.llmModelRegistry = llmModelRegistry;
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
        List<String> subQueries = planSubQueries(query);
        metricsService.countAgentic(subQueries.size());

        Map<String, SearchHitVO> merged = new LinkedHashMap<>();
        for (String sub : subQueries) {
            List<SearchHitVO> hits = singlePass.apply(kbId, sub);
            if (hits == null) {
                continue;
            }
            for (SearchHitVO hit : hits) {
                merged.putIfAbsent(dedupeKey(hit), hit);
                if (merged.size() >= 16) {
                    break;
                }
            }
            if (merged.size() >= 16) {
                break;
            }
        }
        if (merged.isEmpty()) {
            return singlePass.apply(kbId, query);
        }
        return new ArrayList<>(merged.values()).subList(0, Math.min(12, merged.size()));
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
        String raw = llmModelRegistry.getChatModel()
                .chat(SystemMessage.from(DECOMPOSE_PROMPT),
                        UserMessage.from("用户问题：" + query + "\nJSON数组："))
                .aiMessage()
                .text();
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

    static List<String> heuristicSplit(String query) {
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

    private static String dedupeKey(SearchHitVO hit) {
        if ("WIKI".equals(hit.getDocType())) {
            return "wiki:" + hit.getDocumentId();
        }
        return "doc:" + hit.getDocumentId() + ":" + hit.getChunkId();
    }

    @FunctionalInterface
    public interface BiFunctionThrowing<A, B, R> {
        R apply(A a, B b) throws Exception;
    }
}
