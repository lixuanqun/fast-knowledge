package com.fast.knowledge.ai.orchestration;

import com.fast.knowledge.ai.port.ChatPort;
import com.fast.knowledge.audit.AuditActions;
import com.fast.knowledge.common.StringUtils;
import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.mapper.DocumentMapper;
import com.fast.knowledge.mapper.WikiChangeLogMapper;
import com.fast.knowledge.mapper.WikiPageMapper;
import com.fast.knowledge.model.entity.KbDocument;
import com.fast.knowledge.model.entity.WikiChangeLog;
import com.fast.knowledge.model.entity.WikiPage;
import com.fast.knowledge.service.AuditLogService;
import com.fast.knowledge.service.MetricsService;
import com.fast.knowledge.service.TextExtractionService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;

/**
 * Wiki 维护 Agent（v2.0.0 M2）— langgraph4j StateGraph 编排：
 * <pre>
 * START ─┬→ merge（有旧页：LLM 增量合并，保留人工修订）─┐
 *        └→ compile（无旧页：全量编译）───────────────┴→ lint ⇄ revise（≤max 轮）→ END
 * </pre>
 * extract 在图前、persist 在图后（确定性 Java 步骤，短事务）。Lint 规则先行，
 * LLM 语义 Lint 可经 {@code knowledge.wiki.agent.llm-lint-enabled} 关闭以保护本地小模型。
 * 图内任何异常向上抛出，由 {@code WikiCompileService} 回退单发编译路径。
 */
@Slf4j
@Service
public class WikiAgentService {

    static final String NODE_MERGE = "merge";
    static final String NODE_COMPILE = "compile";
    static final String NODE_LINT = "lint";
    static final String NODE_REVISE = "revise";

    private static final String MERGE_SYSTEM = """
            你是 Wiki 维护助手。知识库中已存在该文档对应的 Wiki 页，文档内容发生了更新。
            要求：
            1. 以现有页为基础做增量合并：保留其中仍然有效的内容、人工维护的结构与措辞
            2. 仅按新文档更新冲突或过时的部分（条款号、日期、流程变化）
            3. 不编造原文没有的内容；保留有效内容时不得改变其含义
            4. 文末保留一行「来源文档 ID: {docId}」
            输出合并后的完整 Markdown，不要解释。""";

    private static final String COMPILE_SYSTEM = """
            你是企业知识库 Wiki 编译助手。根据原始文档内容，生成结构化 Markdown 知识页：
            - 包含清晰标题与要点列表
            - 保留文号、制度条款等关键信息（若有）
            - 文末注明「来源文档 ID: {docId}」
            使用简体中文，不要编造原文没有的内容。""";

    private static final String LINT_SYSTEM = """
            检查 Wiki 草稿与文档元数据的一致性，只输出 JSON 字符串数组（无问题输出 []），每项一句话：
            - 草稿中的文号/生效日期/部门与元数据矛盾
            - 草稿内部前后矛盾
            不要检查格式问题。""";

    private static final String REVISE_SYSTEM = """
            按检查意见修订 Wiki 草稿，只输出修订后的完整 Markdown，不要解释。
            保留未涉及意见的内容不变，确保文末保留「来源文档 ID: {docId}」行。""";

    private static final Pattern JSON_ARRAY = Pattern.compile("\\[.*]", Pattern.DOTALL);

    private final ChatPort chatPort;
    private final DocumentMapper documentMapper;
    private final WikiPageMapper wikiPageMapper;
    private final WikiChangeLogMapper wikiChangeLogMapper;
    private final TextExtractionService textExtractionService;
    private final KnowledgeProperties properties;
    private final AuditLogService auditLogService;
    private final MetricsService metricsService;
    private final ObjectMapper objectMapper;
    private final CompiledGraph<WikiAgentState> graph;
    /** 注入自身代理以确保 protected @Transactional persist 方法 AOP 生效（同 WikiCompileService 模式） */
    private final WikiAgentService self;

    public WikiAgentService(ChatPort chatPort,
                            DocumentMapper documentMapper,
                            WikiPageMapper wikiPageMapper,
                            WikiChangeLogMapper wikiChangeLogMapper,
                            TextExtractionService textExtractionService,
                            KnowledgeProperties properties,
                            AuditLogService auditLogService,
                            MetricsService metricsService,
                            ObjectMapper objectMapper,
                            @Lazy WikiAgentService self) {
        this.chatPort = chatPort;
        this.documentMapper = documentMapper;
        this.wikiPageMapper = wikiPageMapper;
        this.wikiChangeLogMapper = wikiChangeLogMapper;
        this.textExtractionService = textExtractionService;
        this.properties = properties;
        this.auditLogService = auditLogService;
        this.metricsService = metricsService;
        this.objectMapper = objectMapper;
        this.self = self;
        this.graph = buildGraph();
    }

    /**
     * 执行维护图并持久化结果。调用方（WikiCompileService）负责任务状态机与异常回退。
     */
    public void run(Long documentId) throws Exception {
        KbDocument doc = documentMapper.selectById(documentId);
        if (doc == null) {
            return;
        }
        String text = StringUtils.truncate(textExtractionService.extractFullText(doc), 12000);
        WikiPage existing = wikiPageMapper.findByKbAndSlug(doc.getKbId(), "doc-" + documentId);

        Map<String, Object> args = new HashMap<>();
        args.put("kbId", doc.getKbId());
        args.put("docId", documentId);
        args.put("title", doc.getTitle() != null ? doc.getTitle() : "");
        args.put("docNo", doc.getDocNo() != null ? doc.getDocNo() : "");
        args.put("meta", StringUtils.buildSourceHint(doc.getTitle(), doc.getDocNo(), doc.getDocType(), null));
        args.put("docText", text);
        args.put("existingMd", existing != null && existing.getContentMd() != null ? existing.getContentMd() : "");
        args.put("iteration", 0);

        WikiAgentState result = graph.invoke(args)
                .orElseThrow(() -> new IllegalStateException("Wiki Agent 图未产生状态"));

        self.persist(doc, existing, result);
    }

    private CompiledGraph<WikiAgentState> buildGraph() {
        try {
            return buildGraphInternal();
        } catch (org.bsc.langgraph4j.GraphStateException e) {
            throw new IllegalStateException("Wiki Agent 图定义非法", e);
        }
    }

    private CompiledGraph<WikiAgentState> buildGraphInternal() throws org.bsc.langgraph4j.GraphStateException {
        NodeAction<WikiAgentState> merge = state -> {
            String draft = chatPort.complete(mergePrompt(state.docId()),
                    mergeUser(state.existingMd(), state.meta(), state.docText()));
            return Map.of("draftMd", draft);
        };
        NodeAction<WikiAgentState> compile = state -> {
            String draft = chatPort.complete(compilePrompt(state.docId()),
                    compileUser(state.meta(), state.docText()));
            return Map.of("draftMd", draft);
        };
        NodeAction<WikiAgentState> lint = state -> {
            List<String> issues = new ArrayList<>(ruleLint(state.draftMd(), state.docNo()));
            if (properties.getWiki().getAgent().isLlmLintEnabled()) {
                issues.addAll(llmLint(state.draftMd(), state.meta()));
            }
            return Map.of("lintIssues", issues);
        };
        NodeAction<WikiAgentState> revise = state -> {
            String revised = chatPort.complete(revisePrompt(state.docId()),
                    reviseUser(state.draftMd(), String.join("\n", state.lintIssues())));
            return Map.of("draftMd", revised, "iteration", state.iteration() + 1);
        };
        EdgeAction<WikiAgentState> afterLint = state -> {
            if (state.lintIssues().isEmpty()) {
                return "end";
            }
            if (state.iteration() >= Math.max(1, properties.getWiki().getAgent().getMaxLintIterations())) {
                return "end";
            }
            return "revise";
        };
        return new StateGraph<WikiAgentState>(WikiAgentState::new)
                .addNode(NODE_MERGE, node_async(merge))
                .addNode(NODE_COMPILE, node_async(compile))
                .addNode(NODE_LINT, node_async(lint))
                .addNode(NODE_REVISE, node_async(revise))
                .addConditionalEdges(START, edge_async(state ->
                                state.existingMd().isBlank() ? NODE_COMPILE : NODE_MERGE),
                        Map.of(NODE_MERGE, NODE_MERGE, NODE_COMPILE, NODE_COMPILE))
                .addEdge(NODE_MERGE, NODE_LINT)
                .addEdge(NODE_COMPILE, NODE_LINT)
                .addConditionalEdges(NODE_LINT, edge_async(afterLint),
                        Map.of(NODE_REVISE, NODE_REVISE, "end", END))
                .addEdge(NODE_REVISE, NODE_LINT)
                .compile(org.bsc.langgraph4j.CompileConfig.builder().build());
    }

    /** 短事务持久化：WikiPage 版本推进 + 变更日志 + 审计 + 指标（经 self 代理调用保证事务生效） */
    @Transactional
    protected void persist(KbDocument doc, WikiPage existing, WikiAgentState result) {
        List<String> issues = result.lintIssues();
        boolean autoPublish = properties.getWiki().isAutoPublish();
        String slug = "doc-" + doc.getId();
        String changeType = existing == null ? "COMPILE" : "MERGE";
        // 状态衔接（设计文档 §4.5）：autoPublish 才直接发布，否则一律 DRAFT 待人工审核
        String status = autoPublish ? "PUBLISHED" : "DRAFT";

        WikiPage page = wikiPageMapper.findByKbAndSlug(doc.getKbId(), slug);
        int fromVersion = page != null && page.getVersion() != null ? page.getVersion() : 0;
        int toVersion = fromVersion + 1;
        if (page == null) {
            page = new WikiPage();
            page.setKbId(doc.getKbId());
            page.setSlug(slug);
            page.setTitle(doc.getTitle());
            page.setContentMd(result.draftMd());
            page.setSourceDocIds(String.valueOf(doc.getId()));
            page.setVersion(toVersion);
            page.setStatus(status);
            try {
                wikiPageMapper.insert(page);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // 并发竞态：另一线程已插入相同 slug，回退为 update
                page = wikiPageMapper.findByKbAndSlug(doc.getKbId(), slug);
                if (page == null) {
                    throw new IllegalStateException("Wiki 页并发插入失败且回退查询也为空: " + slug, e);
                }
                page.setTitle(doc.getTitle());
                page.setContentMd(result.draftMd());
                page.setSourceDocIds(String.valueOf(doc.getId()));
                page.setVersion(toVersion);
                page.setStatus(status);
                wikiPageMapper.updateById(page);
            }
        } else {
            page.setTitle(doc.getTitle());
            page.setContentMd(result.draftMd());
            page.setSourceDocIds(String.valueOf(doc.getId()));
            page.setVersion(toVersion);
            page.setStatus(status);
            wikiPageMapper.updateById(page);
        }

        WikiChangeLog changeLog = new WikiChangeLog();
        changeLog.setKbId(doc.getKbId());
        changeLog.setPageId(page.getId());
        changeLog.setFromVersion(fromVersion == 0 ? null : fromVersion);
        changeLog.setToVersion(toVersion);
        changeLog.setChangeType(changeType);
        changeLog.setSummary(buildSummary(changeType, issues));
        wikiChangeLogMapper.insert(changeLog);

        metricsService.countWikiAgent(changeType);
        auditLogService.log(AuditActions.WIKI_AGENT_MERGE, "WIKI", page.getId(),
                "type=" + changeType + ", version=" + toVersion + ", issues=" + issues.size());
    }

    /** 规则 Lint（确定性）：来源行、标题、长度、文号一致性 */
    static List<String> ruleLint(String draftMd, String docNo) {
        List<String> issues = new ArrayList<>();
        if (draftMd == null || draftMd.isBlank()) {
            issues.add("草稿为空");
            return issues;
        }
        if (draftMd.length() < 50) {
            issues.add("草稿内容过短（<50 字符）");
        }
        if (!draftMd.contains("来源文档 ID")) {
            issues.add("缺少来源文档 ID 行");
        }
        if (!draftMd.contains("#")) {
            issues.add("缺少标题层级");
        }
        if (docNo != null && !docNo.isBlank() && !draftMd.contains(docNo)) {
            issues.add("草稿未包含文号 " + docNo + "，请核对一致性");
        }
        return issues;
    }

    private List<String> llmLint(String draftMd, String meta) {
        try {
            String raw = chatPort.complete(LINT_SYSTEM,
                    "元数据：" + meta + "\n\n草稿：\n" + StringUtils.truncate(draftMd, 6000) + "\nJSON数组：");
            if (raw == null || raw.isBlank()) {
                return List.of();
            }
            Matcher matcher = JSON_ARRAY.matcher(raw.trim());
            String json = matcher.find() ? matcher.group() : raw.trim();
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("Wiki LLM Lint 失败，忽略语义检查: {}", e.getMessage());
            return List.of();
        }
    }

    private static String buildSummary(String changeType, List<String> issues) {
        String base = "COMPILE".equals(changeType) ? "全量编译" : "增量合并（保留原页有效内容）";
        if (issues.isEmpty()) {
            return base + "；Lint 通过";
        }
        return base + "；Lint 未通过(" + issues.size() + "): "
                + StringUtils.truncate(String.join("; ", issues), 600);
    }

    private static String mergePrompt(Long docId) {
        return MERGE_SYSTEM.replace("{docId}", String.valueOf(docId));
    }

    private static String compilePrompt(Long docId) {
        return COMPILE_SYSTEM.replace("{docId}", String.valueOf(docId));
    }

    private static String revisePrompt(Long docId) {
        return REVISE_SYSTEM.replace("{docId}", String.valueOf(docId));
    }

    private static String mergeUser(String existingMd, String meta, String docText) {
        return "文档元数据：" + meta + "\n\n现有 Wiki 页：\n" + StringUtils.truncate(existingMd, 8000)
                + "\n\n最新文档内容：\n" + StringUtils.truncate(docText, 8000);
    }

    private static String compileUser(String meta, String docText) {
        return "文档元数据：" + meta + "\n\n原文摘录：\n" + StringUtils.truncate(docText, 12000);
    }

    private static String reviseUser(String draftMd, String issues) {
        return "检查意见：\n" + issues + "\n\n草稿：\n" + StringUtils.truncate(draftMd, 8000);
    }


}
