package com.fast.knowledge.langchain4j.adapter;

import com.fast.knowledge.ai.port.ChatPort;
import com.fast.knowledge.ai.port.ChunkContextPort;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文前缀生成 — 基于 ChatPort 的批量实现（Anthropic Contextual Retrieval 思路）。
 * 单次调用为一批 chunk 生成上下文，输出 JSON 数组，解析失败按空串降级。
 */
@Slf4j
@Service
public class LangChain4jChunkContextAdapter implements ChunkContextPort {

    private static final String SYSTEM_PROMPT = """
            你是知识库索引工程师。给定一份文档的标题、全文摘录和其中若干片段（片段按顺序编号），
            为每个片段写 1-2 句中文上下文说明，用于弥补片段脱离原文后的语义缺失。要求：
            1. 点明片段在文档中的位置（如"该片段出自《X》的第 N 部分，规定了……"）与核心主题；
            2. 保留文档中的关键实体（部门、设备、文号、数值等专有名词必须原样保留）；
            3. 只依据摘录内容，不要编造；每条不超过 80 字；
            4. 严格输出 JSON 字符串数组，数组长度与片段数量一致，不要输出任何其他文字。
            """;

    private final ChatPort chatPort;
    private final ObjectMapper objectMapper;

    public LangChain4jChunkContextAdapter(ChatPort chatPort, ObjectMapper objectMapper) {
        this.chatPort = chatPort;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<String> generateContexts(String docTitle, String fullTextPreview, List<String> chunks) {
        List<String> result = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            result.add("");
        }
        try {
            StringBuilder userPrompt = new StringBuilder();
            userPrompt.append("文档标题：").append(docTitle == null ? "（无标题）" : docTitle).append("\n");
            if (fullTextPreview != null && !fullTextPreview.isBlank()) {
                userPrompt.append("\n全文摘录（用于理解文档全貌，可能被截断）：\n")
                        .append(fullTextPreview)
                        .append("\n");
            }
            userPrompt.append("\n片段列表（共 ").append(chunks.size()).append(" 个，按顺序）：\n");
            for (int i = 0; i < chunks.size(); i++) {
                userPrompt.append("【片段 ").append(i).append("】\n")
                        .append(clip(chunks.get(i), 600))
                        .append("\n\n");
            }
            userPrompt.append("请输出 JSON 字符串数组（").append(chunks.size()).append(" 个元素，第 i 个元素对应【片段 i】的上下文说明）：");

            String response = chatPort.complete(SYSTEM_PROMPT, userPrompt.toString());
            List<String> contexts = parseArray(response, chunks.size());
            for (int i = 0; i < chunks.size() && i < contexts.size(); i++) {
                String ctx = contexts.get(i);
                result.set(i, ctx == null ? "" : clip(ctx.trim(), 300));
            }
        } catch (Exception e) {
            // 降级：本批前缀为空，索引照常
            log.warn("上下文生成失败（降级为无前缀）: {}", e.getMessage());
        }
        return result;
    }

    /** 容忍模型输出 ```json 围栏或前后杂文字，提取首个 JSON 数组 */
    private List<String> parseArray(String response, int expectedSize) {
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
            List<String> parsed = objectMapper.readValue(text.substring(start, end + 1),
                    new TypeReference<List<String>>() {});
            return parsed.size() == expectedSize ? parsed : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    private String clip(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        String normalized = text.strip();
        return normalized.length() <= maxChars ? normalized : normalized.substring(0, maxChars);
    }
}
