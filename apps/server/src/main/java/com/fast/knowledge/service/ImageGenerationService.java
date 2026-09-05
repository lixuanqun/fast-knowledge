package com.fast.knowledge.service;

import com.fast.knowledge.audit.AuditActions;
import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.config.KnowledgeProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文生图 — DashScope 原生异步任务 API（提交 → 轮询 → 取图）。
 * 任务登记保存在内存（DashScope 端保留任务结果供查询），生成图片 URL 为临时链接（约 24 小时），
 * 持久化到 MinIO 列为后续增强。
 */
@Slf4j
@Service
public class ImageGenerationService {

    private static final String DASHSCOPE_BASE = "https://dashscope.aliyuncs.com";
    private static final String SUBMIT_PATH = "/api/v1/services/aigc/text2image/image-synthesis";
    private static final String TASK_PATH = "/api/v1/tasks/";

    private final KnowledgeProperties properties;
    private final ObjectMapper objectMapper;
    private final com.fast.knowledge.service.AuditLogService auditLogService;
    private final RestClient restClient;
    /** taskId → 提交时的模型名（查询任务时回传） */
    private final Map<String, String> taskModels = new ConcurrentHashMap<>();

    public ImageGenerationService(KnowledgeProperties properties, ObjectMapper objectMapper,
                                  com.fast.knowledge.service.AuditLogService auditLogService) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.auditLogService = auditLogService;
        this.restClient = RestClient.create();
    }

    /** 提交文生图任务，返回 DashScope task_id */
    public String submit(String prompt) {
        KnowledgeProperties.ImageGen cfg = enabledConfig();
        if (prompt == null || prompt.isBlank()) {
            throw new BusinessException("请输入图片描述");
        }
        JsonNode output = postForJson(SUBMIT_PATH, Map.of(
                        "X-DashScope-Async", "enable"),
                Map.of("model", cfg.getModel(),
                        "input", Map.of("prompt", prompt),
                        "parameters", Map.of("size", cfg.getSize(), "n", 1)));
        String taskId = output.path("task_id").asText(null);
        if (taskId == null || taskId.isBlank()) {
            throw new BusinessException("文生图任务提交失败，请稍后重试");
        }
        taskModels.put(taskId, cfg.getModel());
        auditLogService.log("IMAGE_GEN_SUBMIT", "TASK", null,
                "taskId=" + taskId + ", prompt=" + truncate(prompt, 100));
        return taskId;
    }

    /** 查询任务：status ∈ PENDING / RUNNING / SUCCEEDED / FAILED；成功时附 imageUrl */
    public ImageTask query(String taskId) {
        String model = taskModels.get(taskId);
        String key = apiKey();
        JsonNode output = getForJson(TASK_PATH + taskId, key).path("output");
        String status = output.path("task_status").asText("UNKNOWN");
        String imageUrl = null;
        if ("SUCCEEDED".equals(status)) {
            imageUrl = output.path("results").path(0).path("url").asText(null);
        }
        if ("FAILED".equals(status)) {
            String code = output.path("code").asText("");
            throw new BusinessException("文生图任务失败" + (code.isBlank() ? "" : "（" + code + "）"));
        }
        return new ImageTask(taskId, status, imageUrl);
    }

    /** 下载生成的图片字节（代理 DashScope 临时链接，避免前端跨域与链接泄露） */
    public byte[] download(String taskId) {
        String url = query(taskId).imageUrl();
        if (url == null) {
            throw new BusinessException("任务尚未生成图片或已过期");
        }
        // 以 URI 对象传入，避免 RestClient 对签名参数二次编码导致 OSS 校验失败
        byte[] bytes = restClient.get().uri(java.net.URI.create(url)).retrieve().body(byte[].class);
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException("图片下载失败，请重试");
        }
        return bytes;
    }

    private KnowledgeProperties.ImageGen enabledConfig() {
        KnowledgeProperties.ImageGen cfg = properties.getImageGen();
        if (!cfg.isEnabled()) {
            throw new BusinessException("文生图功能未启用");
        }
        if (!properties.getLlm().isAllowExternal()) {
            throw new BusinessException("内网纯离线模式下文生图不可用（生图模型仅云端提供）");
        }
        return cfg;
    }

    private String apiKey() {
        String key = properties.getLlm().getApiKey();
        if (key == null || key.isBlank()) {
            throw new BusinessException("缺少 LLM API Key，请先在「大模型配置」完成配置");
        }
        return key;
    }

    private JsonNode postForJson(String path, Map<String, String> headers, Map<?, ?> body) {
        try {
            String resp = restClient.post()
                    .uri(DASHSCOPE_BASE + path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey())
                    .headers(h -> headers.forEach(h::add))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(resp == null ? "{}" : resp).path("output");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("DashScope 请求失败: {}", e.getMessage());
            throw new BusinessException("调用文生图服务失败，请稍后重试");
        }
    }

    private JsonNode getForJson(String path, String key) {
        try {
            String resp = restClient.get()
                    .uri(DASHSCOPE_BASE + path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + key)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(resp == null ? "{}" : resp);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("DashScope 任务查询失败: {}", e.getMessage());
            throw new BusinessException("查询文生图任务失败，请稍后重试");
        }
    }

    private String truncate(String s, int len) {
        return s.length() <= len ? s : s.substring(0, len) + "...";
    }

    /** 任务查询结果 */
    public record ImageTask(String taskId, String status, String imageUrl) {
    }
}
