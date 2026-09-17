package com.fast.knowledge.llm;

import com.fast.knowledge.ai.port.ChatPort;
import com.fast.knowledge.mapper.LlmTraceMapper;
import com.fast.knowledge.model.entity.LlmTrace;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * WP10 ChatPort 装饰器 — 包装底层 ChatPort 实现，对每次 complete() 自动埋点。
 *
 * <p>场景推断：从调用栈路径识别（chunk_context / rerank / kg_extract / agentic_decompose /
 * agentic_critique / ocr / wiki / writer / qa / chat），供用量看板按场景聚合。
 * 调用方零改动：Spring 注入 ChatPort 时优先注入本装饰器（@Primary）。
 */
@Slf4j
@Service
@Primary
public class TraceableChatPort implements ChatPort {

    private final ChatPort delegate;
    private final LlmTraceMapper llmTraceMapper;
    private final LlmConfigResolver llmConfigResolver;
    private final ThreadLocal<String> sceneOverride = new ThreadLocal<>();
    private final ThreadLocal<String> correlationId = new ThreadLocal<>();

    public TraceableChatPort(@Qualifier("langChain4jChatAdapter") ChatPort delegate,
                             LlmTraceMapper llmTraceMapper,
                             LlmConfigResolver llmConfigResolver) {
        this.delegate = delegate;
        this.llmTraceMapper = llmTraceMapper;
        this.llmConfigResolver = llmConfigResolver;
    }

    /** 显式设置场景与关联 ID（调用方可选；不设置则从栈推断） */
    public void withContext(String scene, String correlationId) {
        sceneOverride.set(scene);
        if (correlationId != null) {
            this.correlationId.set(correlationId);
        }
    }

    public void clearContext() {
        sceneOverride.remove();
        correlationId.remove();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        String scene = sceneOverride.get() != null ? sceneOverride.get() : inferScene(systemPrompt);
        long start = System.currentTimeMillis();
        String response = null;
        String error = null;
        try {
            response = delegate.complete(systemPrompt, userPrompt);
            return response;
        } catch (RuntimeException e) {
            error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            throw e;
        } finally {
            recordTrace(scene, systemPrompt, userPrompt, response, error,
                    (int) (System.currentTimeMillis() - start));
        }
    }

    @Override
    public void stream(String systemPrompt, String userPrompt, StreamHandler handler) {
        // 流式调用由 ChatServiceImpl 经 ConversationPort 处理，ChatPort.stream 不走装饰器埋点
        delegate.stream(systemPrompt, userPrompt, handler);
    }

    private void recordTrace(String scene, String systemPrompt, String userPrompt,
                             String response, String error, int latencyMs) {
        try {
            ResolvedLlmConfig cfg = llmConfigResolver.resolve();
            LlmTrace trace = new LlmTrace();
            trace.setScene(scene);
            trace.setProvider(cfg.getProvider().getId());
            trace.setModel(cfg.getModel() != null ? cfg.getModel() : "unknown");
            trace.setLatencyMs(latencyMs);
            trace.setPromptChars((systemPrompt == null ? 0 : systemPrompt.length())
                    + (userPrompt == null ? 0 : userPrompt.length()));
            trace.setResponseChars(response != null ? response.length() : 0);
            trace.setStatus(error == null ? "OK" : "ERROR");
            trace.setError(error);
            trace.setCorrelationId(correlationId.get());
            llmTraceMapper.insert(trace);
        } catch (Exception e) {
            // trace 落库失败不影响主流程
            log.debug("LLM trace 落库失败: {}", e.getMessage());
        }
    }

    /**
     * 场景推断：显式 withContext 优先；否则从 systemPrompt 关键词识别
     * （栈帧匹配在 langchain4j 异步/代理调用下不可靠，prompt 内容特征最稳定）。
     */
    private String inferScene(String systemPrompt) {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (int i = 3; i < Math.min(stack.length, 30); i++) {
            String cls = stack[i].getClassName();
            String mtd = stack[i].getMethodName();
            if (cls.contains("ChunkContextAdapter")) return "chunk_context";
            if (cls.contains("KnowledgeGraphService")) return "kg_extract";
            if (cls.contains("AgenticRetrievalService")) {
                return "evaluateSufficiency".equals(mtd) ? "agentic_critique" : "agentic_decompose";
            }
            if (cls.contains("OcrParseService")) return "ocr";
            if (cls.contains("WikiAgentService")) return "wiki";
            if (cls.contains("WriterGraphService")) return "writer";
        }
        // prompt 内容特征兜底（langchain4j 内部调用栈帧不可达时）
        if (systemPrompt != null) {
            // 用稳定短子串匹配，容忍 text block 前导空格
            if (systemPrompt.contains("相关性分")) return "rerank";
            if (systemPrompt.contains("上下文说明")) return "chunk_context";
            if (systemPrompt.contains("图谱构建助手")) return "kg_extract";
            if (systemPrompt.contains("检索质量评估器")) return "agentic_critique";
            if (systemPrompt.contains("检索规划助手")) return "agentic_decompose";
            if (systemPrompt.contains("文档数字化")) return "ocr";
        }
        return "unknown";
    }
}
