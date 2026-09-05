package com.fast.knowledge.service;

import com.fast.knowledge.audit.AuditActions;
import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.config.KnowledgeProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fast.knowledge.common.StringUtils;
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
    private final com.fast.knowledge.storage.StorageProvider storageProvider;
    private final RestClient restClient;
    /** 已持久化到存储资产的任务 ID（懒持久化：首次 SUCCEEDED 查询时落存储） */
    private final java.util.Set<String> persistedTasks = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** taskId → 提交时的模型名（查询任务时回传） */
    private final Map<String, String> taskModels = new ConcurrentHashMap<>();

    public ImageGenerationService(KnowledgeProperties properties, ObjectMapper objectMapper,
                                  com.fast.knowledge.service.AuditLogService auditLogService,
                                  com.fast.knowledge.storage.StorageProvider storageProvider) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.auditLogService = auditLogService;
        this.storageProvider = storageProvider;
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
                "taskId=" + taskId + ", prompt=" + StringUtils.truncateEllipsis(prompt, 100));
        return taskId;
    }

    /** 查询任务：status ∈ PENDING / RUNNING / SUCCEEDED / FAILED；成功时附 imageUrl */
    public ImageTask query(String taskId) {
        String model = taskModels.get(taskId);
        String key = apiKey();
        JsonNode output = getForJson(TASK_PATH + taskId, key).path("output");
        String status = output.path("task_status").asText("UNKNOWN");
        if ("FAILED".equals(status)) {
            String code = output.path("code").asText("");
            throw new BusinessException("文生图任务失败" + (code.isBlank() ? "" : "（" + code + "）"));
        }
        boolean persisted = false;
        if ("SUCCEEDED".equals(status)) {
            persisted = persistIfAbsent(taskId, output.path("results").path(0).path("url").asText(null));
        }
        return new ImageTask(taskId, status, persisted);
    }

    /**
     * 懒持久化：任务 SUCCEEDED 后首次查询时把图片拉回并写入存储资产
     * （DashScope 结果链接约 24 小时过期，不能依赖）；失败时保留临时链接兜底。
     */
    private boolean persistIfAbsent(String taskId, String url) {
        if (url == null || url.isBlank() || persistedTasks.contains(taskId)) {
            return persistedTasks.contains(taskId);
        }
        try {
            // 以 URI 对象传入，避免 RestClient 对签名参数二次编码
            byte[] bytes = restClient.get().uri(java.net.URI.create(url)).retrieve().body(byte[].class);
            if (bytes != null && bytes.length > 0) {
                storageProvider.storeAsset("image-gen/" + taskId + ".png", bytes);
                persistedTasks.add(taskId);
                return true;
            }
        } catch (Exception e) {
            log.warn("生成图片持久化失败（保留 DashScope 临时链接兜底）: taskId={}, {}", taskId, e.getMessage());
        }
        return false;
    }

    /** 读取任务图片字节：优先已持久化资产，否则回源 DashScope 临时链接 */
    public byte[] imageBytes(String taskId) {
        byte[] stored = storageProvider.getAsset("image-gen/" + taskId + ".png");
        if (stored != null && stored.length > 0) {
            return stored;
        }
        JsonNode output = getForJson(TASK_PATH + taskId, apiKey()).path("output");
        String url = output.path("results").path(0).path("url").asText(null);
        if (url == null || url.isBlank()) {
            throw new BusinessException("任务尚未生成图片或已过期");
        }
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



    /** 任务查询结果（persisted=true 表示图片已持久化到存储资产） */
    public record ImageTask(String taskId, String status, boolean persisted) {
    }
}
