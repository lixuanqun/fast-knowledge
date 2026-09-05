package com.fast.knowledge.ai.orchestration;

import com.fast.knowledge.ai.port.ChatPort;
import com.fast.knowledge.common.SseEmitterHelper;
import com.fast.knowledge.model.dto.WriterRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;

/**
 * AI 写文档多步编排（langgraph4j StateGraph）：
 * <pre>
 * START → planOutline → draftSection（按大纲循环，流式） → cite → polish（流式） → END
 * </pre>
 * 流式策略：节点内部经 ChatPort.stream 手工推 SSE（agentic/图只做编排），
 * 保持 [DONE] 契约；每节点进入时发送 {@code step} 命名事件（旧前端忽略未知事件，向后兼容）。
 */
@Slf4j
@Service
public class WriterGraphService {

    static final String NODE_OUTLINE = "planOutline";
    static final String NODE_SECTION = "draftSection";
    static final String NODE_CITE = "cite";
    static final String NODE_POLISH = "polish";

    private static final String OUTLINE_SYSTEM = """
            你是文档大纲规划助手。根据主题与参考资料，输出 Markdown 有序列表大纲，每行形如「1. 小节标题」。
            要求：3～6 个小节，覆盖主题的核心方面，不编造参考资料之外的结论。""";

    private static final String SECTION_SYSTEM = """
            你是中文文档写作助手。根据大纲为指定小节撰写正文（Markdown）：
            - 只写这一小节，标题用二级标题「## 小节标题」
            - 基于参考资料，不编造；无参考资料时给出通用但严谨的表述
            - 篇幅与全文字数要求匹配""";

    private static final String POLISH_SYSTEM = """
            你是文档润色助手。对给定 Markdown 全文做最终校订：
            - 保持结构与事实不变，修正衔接、重复与格式问题
            - 直接输出完整 Markdown，不要解释""";

    private final ChatPort chatPort;
    private final ObjectMapper objectMapper;
    private final CompiledGraph<WriterState> compiledGraph;
    private final ThreadLocal<SseEmitter> currentEmitter = new ThreadLocal<>();

    public WriterGraphService(ChatPort chatPort, ObjectMapper objectMapper) {
        this.chatPort = chatPort;
        this.objectMapper = objectMapper;
        try {
            this.compiledGraph = buildGraph();
        } catch (org.bsc.langgraph4j.GraphStateException e) {
            throw new IllegalStateException("写文档图定义非法", e);
        }
    }

    /** 执行写文档图；调用方（WriterService）在图完成后发送 [DONE] */
    public void generate(WriterRequest request, SseEmitter emitter, String ragContext) {
        currentEmitter.set(emitter);
        try {
            Map<String, Object> args = new HashMap<>();
            args.put("topic", request.getTopic());
            args.put("outlineHint", request.getOutline() != null ? request.getOutline() : "");
            args.put("style", request.getStyle() != null ? request.getStyle() : "正式、专业");
            args.put("wordCount", request.getWordCount() != null ? String.valueOf(request.getWordCount()) : "800");
            args.put("context", ragContext != null ? ragContext : "");
            args.put("sections", new ArrayList<String>());
            args.put("sectionTexts", new ArrayList<String>());
            args.put("index", 0);

            compiledGraph.invoke(args).orElseThrow(() -> new IllegalStateException("写文档图未产生状态"));
        } finally {
            currentEmitter.remove();
        }
    }

    private CompiledGraph<WriterState> buildGraph() throws org.bsc.langgraph4j.GraphStateException {
        NodeAction<WriterState> outline = state -> {
            String outlineMd = chatPort.complete(OUTLINE_SYSTEM, outlineUser(state));
            List<String> sections = parseOutline(outlineMd);
            return Map.of("sections", sections);
        };
        NodeAction<WriterState> section = state -> {
            SseEmitter emitter = currentEmitter.get();
            int index = state.index();
            List<String> sections = state.sections();
            String title = sections.get(index);
            sendStep(emitter, "draftSection", index + 1, sections.size(), title);
            StringBuilder buffer = new StringBuilder();
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
            chatPort.stream(SECTION_SYSTEM, sectionUser(state, title), new ChatPort.StreamHandler() {
                @Override
                public void onPartial(String partial) {
                    buffer.append(partial);
                    try {
                        SseEmitterHelper.sendData(emitter, partial);
                    } catch (Exception e) {
                        log.debug("推送分节内容失败（客户端可能已断开）: {}", e.getMessage());
                        latch.countDown(); // release latch on client disconnect
                    }
                }

                @Override
                public void onComplete(String fullText) {
                    if (!fullText.isBlank()) {
                        buffer.setLength(0);
                        buffer.append(fullText);
                    }
                    latch.countDown();
                }

                @Override
                public void onError(Throwable error) {
                    failure.set(error);
                    latch.countDown();
                }
            });
            awaitStream(latch, failure, "分节生成失败: " + title);
            List<String> texts = new ArrayList<>(state.sectionTexts());
            texts.add(buffer.toString());
            return Map.of("sectionTexts", texts, "index", index + 1);
        };
        EdgeAction<WriterState> afterSection = state ->
                state.index() < state.sections().size() ? NODE_SECTION : NODE_CITE;
        NodeAction<WriterState> cite = state -> {
            SseEmitter emitter = currentEmitter.get();
            sendStep(emitter, NODE_CITE, 0, 0, null);
            StringBuilder full = new StringBuilder("# ").append(state.topic()).append("\n\n");
            for (String text : state.sectionTexts()) {
                full.append(text.strip()).append("\n\n");
            }
            String context = state.context();
            if (!context.isBlank()) {
                full.append("## 参考资料\n\n")
                        .append("<references>\n").append(context).append("\n</references>\n");
            }
            return Map.of("citedMd", full.toString());
        };
        NodeAction<WriterState> polish = state -> {
            SseEmitter emitter = currentEmitter.get();
            sendStep(emitter, NODE_POLISH, 0, 0, null);
            StringBuilder buffer = new StringBuilder();
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
            chatPort.stream(POLISH_SYSTEM, state.citedMd(), new ChatPort.StreamHandler() {
                @Override
                public void onPartial(String partial) {
                    buffer.append(partial);
                    try {
                        SseEmitterHelper.sendData(emitter, partial);
                    } catch (Exception e) {
                        log.debug("推送润色内容失败（客户端可能已断开）: {}", e.getMessage());
                        latch.countDown(); // release latch on client disconnect
                    }
                }

                @Override
                public void onComplete(String fullText) {
                    if (!fullText.isBlank()) {
                        buffer.setLength(0);
                        buffer.append(fullText);
                    }
                    latch.countDown();
                }

                @Override
                public void onError(Throwable error) {
                    failure.set(error);
                    latch.countDown();
                }
            });
            awaitStream(latch, failure, "润色失败");
            String finalMd = buffer.toString().isBlank() ? state.citedMd() : buffer.toString();
            return Map.of("finalMd", finalMd);
        };
        return new StateGraph<WriterState>(WriterState::new)
                .addNode(NODE_OUTLINE, node_async(outline))
                .addNode(NODE_SECTION, node_async(section))
                .addNode(NODE_CITE, node_async(cite))
                .addNode(NODE_POLISH, node_async(polish))
                .addEdge(START, NODE_OUTLINE)
                .addEdge(NODE_OUTLINE, NODE_SECTION)
                .addConditionalEdges(NODE_SECTION, edge_async(afterSection),
                        Map.of(NODE_SECTION, NODE_SECTION, NODE_CITE, NODE_CITE))
                .addEdge(NODE_CITE, NODE_POLISH)
                .addEdge(NODE_POLISH, END)
                .compile(org.bsc.langgraph4j.CompileConfig.builder().build());
    }

    /** 流式节点必须阻塞等待回调完成（langgraph4j 节点返回即代表状态就绪） */
    private static void awaitStream(java.util.concurrent.CountDownLatch latch,
                                    java.util.concurrent.atomic.AtomicReference<Throwable> failure,
                                    String what) {
        try {
            if (!latch.await(180, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new IllegalStateException(what + "：流式响应超时");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(what + "：等待被中断", e);
        }
        Throwable error = failure.get();
        if (error != null) {
            throw new IllegalStateException(what, error);
        }
    }

    private void sendStep(SseEmitter emitter, String stage, int index, int total, String title) {
        try {
            Map<String, Object> step = new HashMap<>();
            step.put("stage", stage);
            step.put("sectionIndex", index);
            step.put("sectionTotal", total);
            if (title != null) {
                step.put("title", title);
            }
            SseEmitterHelper.sendNamed(emitter, "step", objectMapper.writeValueAsString(step));
        } catch (Exception e) {
            log.debug("发送 step 事件失败（客户端可能已断开）: {}", e.getMessage());
        }
    }

    static List<String> parseOutline(String outlineMd) {
        List<String> sections = new ArrayList<>();
        for (String line : outlineMd.split("\n")) {
            String trimmed = line.strip();
            if (trimmed.matches("^\\d+[.、)．]\\s*\\S.*")) {
                String title = trimmed.replaceFirst("^\\d+[.、)．]\\s*", "").strip();
                if (!title.isEmpty()) {
                    sections.add(title);
                }
            }
        }
        return sections.isEmpty() ? List.of("概述") : sections;
    }

    private static String outlineUser(WriterState state) {
        StringBuilder sb = new StringBuilder("主题：").append(state.topic()).append("\n")
                .append("风格：").append(state.style()).append("\n")
                .append("目标字数：").append(state.wordCount()).append("\n");
        if (!state.outlineHint().isBlank()) {
            sb.append("用户已有大纲要求：").append(state.outlineHint()).append("\n");
        }
        sb.append("\n参考资料：\n").append(state.context().isBlank() ? "（无）" : state.context());
        return sb.toString();
    }

    private static String sectionUser(WriterState state, String title) {
        return "全篇主题：" + state.topic() + "\n小节：" + title
                + "\n风格：" + state.style() + "，全篇目标字数：" + state.wordCount()
                + "\n\n参考资料：\n" + (state.context().isBlank() ? "（无）" : state.context());
    }

    /** 写文档图状态 */
    public static class WriterState extends AgentState {

        public WriterState(Map<String, Object> state) {
            super(state);
        }

        public String topic() {
            return value("topic", (String) null);
        }

        public String outlineHint() {
            return value("outlineHint", "");
        }

        public String style() {
            return value("style", "");
        }

        public String wordCount() {
            return value("wordCount", "");
        }

        public String context() {
            return value("context", "");
        }

        public List<String> sections() {
            List<String> sections = value("sections", (List<String>) null);
            return sections != null ? sections : List.of();
        }

        public List<String> sectionTexts() {
            List<String> texts = value("sectionTexts", (List<String>) null);
            return texts != null ? texts : List.of();
        }

        public int index() {
            return value("index", 0);
        }

        public String citedMd() {
            return value("citedMd", "");
        }
    }
}
