package com.fast.knowledge.controller;

import com.fast.knowledge.ai.orchestration.retrieval.RetrievalOrchestrator;
import com.fast.knowledge.common.ApiResponse;
import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.mapper.DocumentChunkMapper;
import com.fast.knowledge.service.RagService;
import com.fast.knowledge.model.dto.SearchRequest;
import com.fast.knowledge.model.entity.DocumentChunk;
import com.fast.knowledge.model.entity.KnowledgeBase;
import com.fast.knowledge.model.vo.SearchHitVO;
import com.fast.knowledge.service.AuditLogService;
import com.fast.knowledge.service.KnowledgeBaseService;
import com.fast.knowledge.service.SearchService;
import com.fast.knowledge.service.RagService;
import com.fast.knowledge.model.dto.QaRequest;
import com.fast.knowledge.model.vo.QaResponseVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WP8 MCP Server — Model Context Protocol over Streamable HTTP（JSON-RPC 2.0）。
 *
 * <p>零依赖自实现（MCP 2025-03-26 规范的无状态模式）：客户端每次 POST 独立请求，
 * 支持 initialize / tools/list / tools/call / ping。工具面向企业内其他 AI 工具
 * （IDE 助手、IM 机器人、自研 Agent）暴露知识库检索与问答能力。
 *
 * <p>鉴权：复用 API Key（X-API-Key 或 Authorization: ApiKey），JwtAuthenticationFilter
 * 建立身份，scoped key 自动限定知识库；审计复用 AuditLogService。
 */
@Slf4j
@RestController
@RequestMapping("/mcp")
public class McpController {

    private static final String PROTOCOL_VERSION = "2025-03-26";
    private static final String SERVER_NAME = "fast-knowledge";

    private final KnowledgeProperties properties;
    private final ObjectMapper objectMapper;
    private final KnowledgeBaseService knowledgeBaseService;
    private final SearchService searchService;
    private final RagService ragService;
    private final DocumentChunkMapper documentChunkMapper;
    private final AuditLogService auditLogService;

    public McpController(KnowledgeProperties properties,
                         ObjectMapper objectMapper,
                         KnowledgeBaseService knowledgeBaseService,
                         SearchService searchService,
                         RagService ragService,
                         DocumentChunkMapper documentChunkMapper,
                         AuditLogService auditLogService) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.knowledgeBaseService = knowledgeBaseService;
        this.searchService = searchService;
        this.ragService = ragService;
        this.documentChunkMapper = documentChunkMapper;
        this.auditLogService = auditLogService;
    }

    @PostMapping
    public Map<String, Object> handle(@RequestBody JsonNode request) {
        String method = request.path("method").asText("");
        JsonNode id = request.get("id");
        boolean isNotification = !request.has("id");
        ObjectNode params = request.get("params") != null && request.get("params").isObject()
                ? (ObjectNode) request.get("params") : objectMapper.createObjectNode();

        try {
            // 通知无响应体
            if (isNotification) {
                return null;
            }
            switch (method) {
                case "initialize":
                    return result(id, initialize());
                case "ping":
                    return result(id, objectMapper.createObjectNode());
                case "tools/list":
                    if (!properties.getMcp().isEnabled()) {
                        return error(id, -32000, "MCP 已关闭（KNOWLEDGE_MCP_ENABLED=false）");
                    }
                    return result(id, toolsList());
                case "tools/call":
                    if (!properties.getMcp().isEnabled()) {
                        return error(id, -32000, "MCP 已关闭（KNOWLEDGE_MCP_ENABLED=false）");
                    }
                    return result(id, toolsCall(params));
                default:
                    return error(id, -32601, "method not found: " + method);
            }
        } catch (IllegalArgumentException e) {
            return error(id, -32602, e.getMessage());
        } catch (Exception e) {
            log.warn("MCP 调用失败 method={}: {}", method, e.getMessage());
            return error(id, -32000, e.getMessage() == null ? "internal error" : e.getMessage());
        }
    }

    // ---- MCP methods ----

    private ObjectNode initialize() {
        ObjectNode serverInfo = objectMapper.createObjectNode();
        serverInfo.put("name", SERVER_NAME);
        serverInfo.put("version", "2.2.0");
        ObjectNode result = objectMapper.createObjectNode();
        result.put("protocolVersion", PROTOCOL_VERSION);
        result.putObject("capabilities").putObject("tools").put("listChanged", false);
        result.putObject("serverInfo").setAll(serverInfo);
        return result;
    }

    private ObjectNode toolsList() {
        ArrayNode tools = objectMapper.createArrayNode();
        tools.add(tool("list_kbs",
                "列出当前凭证可访问的知识库（返回 id 与名称）",
                objectNode().put("type", "object").put("additionalProperties", false)));
        tools.add(tool("search",
                "在指定知识库中混合检索（向量+关键词），返回相关片段列表",
                stringIntSchema("kbId", "query", "topK")));
        tools.add(tool("qa",
                "在指定知识库中问答（RAG，返回带引用来源的答案）",
                stringIntSchema("kbId", "question", null)));
        tools.add(tool("fetch_chunk",
                "按 ID 获取单个分块的完整内容与元数据",
                intSchema("chunkId", "分块 ID")));
        ObjectNode result = objectMapper.createObjectNode();
        result.set("tools", tools);
        return result;
    }

    /** {kbId:integer 必填, query/question:string 必填, topK:integer 可选} 的输入 schema */
    private ObjectNode stringIntSchema(String intName, String stringName, String optionalInt) {
        ObjectNode schema = objectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        ObjectNode props = schema.putObject("properties");
        ObjectNode intProp = props.putObject(intName);
        intProp.put("type", "integer");
        intProp.put("description", "目标知识库 ID");
        ObjectNode strProp = props.putObject(stringName);
        strProp.put("type", "string");
        strProp.put("description", optionalInt != null ? "检索关键词" : "问题");
        if (optionalInt != null) {
            ObjectNode k = props.putObject(optionalInt);
            k.put("type", "integer");
            k.put("description", "返回条数上限，默认 8");
        }
        ArrayNode required = schema.putArray("required");
        required.add(intName);
        required.add(stringName);
        return schema;
    }

    private ObjectNode intSchema(String name, String description) {
        ObjectNode schema = objectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        ObjectNode props = schema.putObject("properties");
        ObjectNode p = props.putObject(name);
        p.put("type", "integer");
        p.put("description", description);
        ArrayNode required = schema.putArray("required");
        required.add(name);
        return schema;
    }

    private ObjectNode toolsCall(ObjectNode params) {
        String toolName = params.path("name").asText("");
        JsonNode args = params.get("arguments");
        Map<String, Object> argsMap = objectMapper.convertValue(
                args != null && args.isObject() ? args : objectMapper.createObjectNode(),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                });
        String text;
        try {
            switch (toolName) {
                case "list_kbs":
                    text = callListKbs();
                    break;
                case "search":
                    text = callSearch(argsMap);
                    break;
                case "qa":
                    text = callQa(argsMap);
                    break;
                case "fetch_chunk":
                    text = callFetchChunk(argsMap);
                    break;
                default:
                    return errorResult("unknown tool: " + toolName);
            }
        } catch (IllegalArgumentException e) {
            return errorResult(e.getMessage());
        } catch (Exception e) {
            log.warn("MCP tool {} 执行失败: {}", toolName, e.getMessage());
            return errorResult("tool execution failed: " + e.getMessage());
        }
        auditLogService.log("MCP_TOOL_CALL", "MCP", null,
                "tool=" + toolName + ", args=" + safeArgs(argsMap));
        ObjectNode result = objectMapper.createObjectNode();
        ObjectNode content = result.putArray("content").addObject();
        content.put("type", "text");
        content.put("text", text);
        result.put("isError", false);
        return result;
    }

    // ---- 工具实现 ----

    private String callListKbs() {
        List<KnowledgeBase> kbs = knowledgeBaseService.listMine();
        StringBuilder sb = new StringBuilder("共 ").append(kbs.size()).append(" 个知识库：\n");
        for (KnowledgeBase kb : kbs) {
            sb.append("- id=").append(kb.getId()).append("  ").append(kb.getName()).append("\n");
        }
        return sb.toString();
    }

    private String callSearch(Map<String, Object> args) throws Exception {
        Long kbId = requireLong(args, "kbId");
        String query = requireString(args, "query");
        SearchRequest request = new SearchRequest();
        request.setKbId(kbId);
        request.setQuery(query);
        request.setTopK(intArg(args, "topK", null));
        List<SearchHitVO> hits = searchService.search(request);
        StringBuilder sb = new StringBuilder("找到 ").append(hits.size()).append(" 条相关片段：\n\n");
        int i = 1;
        for (SearchHitVO hit : hits) {
            sb.append("【").append(i++).append("】").append(hit.getDocumentTitle());
            if (hit.getSection() != null && !hit.getSection().isBlank()) {
                sb.append(" / ").append(hit.getSection());
            }
            sb.append("（chunkId=").append(hit.getChunkId())
                    .append(", score=").append(String.format("%.3f", hit.getScore())).append("）\n")
                    .append(hit.getContent(), 0, Math.min(hit.getContent().length(), 400)).append("\n\n");
        }
        return sb.toString();
    }

    private String callQa(Map<String, Object> args) throws Exception {
        Long kbId = requireLong(args, "kbId");
        String question = requireString(args, "question");
        QaRequest request = new QaRequest();
        request.setKbId(kbId);
        request.setQuestion(question);
        QaResponseVO response = ragService.ask(request);
        StringBuilder sb = new StringBuilder(response.getAnswer()).append("\n\n—— 引用来源 ——\n");
        List<SearchHitVO> sources = response.getSources();
        if (sources != null) {
            for (SearchHitVO s : sources) {
                sb.append("- ").append(s.getDocumentTitle());
                if (s.getSection() != null && !s.getSection().isBlank()) {
                    sb.append(" / ").append(s.getSection());
                }
                sb.append("（chunkId=").append(s.getChunkId()).append("）\n");
            }
        }
        return sb.toString();
    }

    private String callFetchChunk(Map<String, Object> args) {
        Long chunkId = requireLong(args, "chunkId");
        DocumentChunk chunk = documentChunkMapper.selectById(chunkId);
        if (chunk == null) {
            throw new IllegalArgumentException("分块不存在: " + chunkId);
        }
        StringBuilder sb = new StringBuilder("chunkId=").append(chunk.getId())
                .append("  docId=").append(chunk.getDocumentId())
                .append("  index=").append(chunk.getChunkIndex()).append("\n");
        if (chunk.getSectionTitle() != null) {
            sb.append("section: ").append(chunk.getSectionTitle()).append("\n");
        }
        sb.append("\n").append(chunk.getContent());
        return sb.toString();
    }

    // ---- 参数与工具定义辅助 ----

    private static Long requireLong(Map<String, Object> args, String name) {
        Object v = args.get(name);
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v instanceof String s && s.matches("\\d+")) {
            return Long.parseLong(s);
        }
        throw new IllegalArgumentException("缺少必填参数 " + name + "（整数）");
    }

    private static String requireString(Map<String, Object> args, String name) {
        Object v = args.get(name);
        if (v instanceof String s && !s.isBlank()) {
            return s;
        }
        throw new IllegalArgumentException("缺少必填参数 " + name + "（字符串）");
    }

    private static Integer intArg(Map<String, Object> args, String name, Integer def) {
        Object v = args.get(name);
        return v instanceof Number n ? n.intValue() : def;
    }

    private static String safeArgs(Map<String, Object> args) {
        return args.toString();
    }

    private ObjectNode tool(String name, String description, ObjectNode inputSchema) {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("name", name);
        tool.put("description", description);
        tool.set("inputSchema", inputSchema);
        return tool;
    }

    private ObjectNode objectNode() {
        return objectMapper.createObjectNode();
    }

    private ObjectNode errorResult(String message) {
        ObjectNode result = objectMapper.createObjectNode();
        ObjectNode content = result.putArray("content").addObject();
        content.put("type", "text");
        content.put("text", message);
        result.put("isError", true);
        return result;
    }

    private Map<String, Object> result(JsonNode id, ObjectNode payload) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("jsonrpc", "2.0");
        resp.put("id", id);
        resp.put("result", payload);
        return resp;
    }

    private Map<String, Object> error(JsonNode id, int code, String message) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("jsonrpc", "2.0");
        resp.put("id", id);
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("code", code);
        err.put("message", message);
        resp.put("error", err);
        return resp;
    }
}
