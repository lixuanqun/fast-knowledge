package com.fast.knowledge.langchain4j.rerank;

import com.fast.knowledge.ai.port.ChatPort;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM Listwise 重排 — 以已配置的对话模型（OpenAI 兼容）给候选片段打相关性分。
 * 单次调用批量打分（0-100 整数），解析失败按中性分 0.5 保序降级，不阻塞检索。
 * 适用场景：无 Cohere/Jina Key 但已配置云端 LLM；内网纯离线模式不可用（需外连）。
 */
@Slf4j
public class LangChain4jLlmScoringModel implements ScoringModel {

    private static final String SYSTEM_PROMPT = """
            你是搜索相关性评估器。给定一个查询和若干编号片段，为每个片段打相关性分（0-100 整数）：
            100=完全回答查询，70+=高度相关，40-69=部分相关，1-39=弱相关，0=无关。
            只依据片段内容判断，不要编造。严格输出 JSON 整数数组（长度与片段数一致），不要输出任何其他文字。
            """;

    private final ChatPort chatPort;
    private final ObjectMapper objectMapper;

    public LangChain4jLlmScoringModel(ChatPort chatPort, ObjectMapper objectMapper) {
        this.chatPort = chatPort;
        this.objectMapper = objectMapper;
    }

    @Override
    public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
        chatPort.withContext("rerank", null);
        List<Double> scores = new ArrayList<>(segments.size());
        for (int i = 0; i < segments.size(); i++) {
            scores.add(0.5);
        }
        try {
            StringBuilder userPrompt = new StringBuilder();
            userPrompt.append("查询：").append(query).append("\n\n片段列表（共 ")
                    .append(segments.size()).append(" 个）：\n");
            for (int i = 0; i < segments.size(); i++) {
                userPrompt.append("【片段 ").append(i).append("】\n")
                        .append(clip(segments.get(i).text(), 500))
                        .append("\n\n");
            }
            userPrompt.append("请输出 JSON 整数数组（").append(segments.size()).append(" 个元素，第 i 个对应【片段 i】）：");

            String response = chatPort.complete(SYSTEM_PROMPT, userPrompt.toString());
            List<Double> parsed = parseScores(response, segments.size());
            if (!parsed.isEmpty()) {
                for (int i = 0; i < segments.size() && i < parsed.size(); i++) {
                    scores.set(i, parsed.get(i));
                }
            }
        } catch (Exception e) {
            log.warn("LLM 重排打分失败（降级为中性分保序）: {}", e.getMessage());
        } finally {
            chatPort.clearContext();
        }
        return Response.from(scores);
    }

    /** 容忍 ```json 围栏或杂文字，提取首个 JSON 数组；长度不符返回空（调用方保序降级） */
    private List<Double> parseScores(String response, int expectedSize) {
        if (response == null || response.isBlank()) {
            return List.of();
        }
        String text = response.trim();
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return List.of();
        }
        try {
            List<Integer> raw = objectMapper.readValue(text.substring(start, end + 1),
                    new TypeReference<List<Integer>>() {});
            if (raw.size() != expectedSize) {
                return List.of();
            }
            List<Double> scores = new ArrayList<>(raw.size());
            for (Integer v : raw) {
                double d = v == null ? 50 : v;
                scores.add(Math.max(0, Math.min(100, d)) / 100.0);
            }
            return scores;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String clip(String text, int maxChars) {
        String normalized = text == null ? "" : text.strip();
        return normalized.length() <= maxChars ? normalized : normalized.substring(0, maxChars);
    }
}
