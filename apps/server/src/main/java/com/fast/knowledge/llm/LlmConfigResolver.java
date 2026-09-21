package com.fast.knowledge.llm;

import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.security.ExternalAccessGuard;
import com.fast.knowledge.service.LlmSettingsService;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class LlmConfigResolver {

    private final KnowledgeProperties properties;
    private final ExternalAccessGuard externalAccessGuard;
    private final LlmSettingsService llmSettingsService;

    public LlmConfigResolver(KnowledgeProperties properties,
                             ExternalAccessGuard externalAccessGuard,
                             LlmSettingsService llmSettingsService) {
        this.properties = properties;
        this.externalAccessGuard = externalAccessGuard;
        this.llmSettingsService = llmSettingsService;
    }

    public ResolvedLlmConfig resolve() {
        return resolve(llmSettingsService.getEffectiveLlm());
    }

    public ResolvedLlmConfig resolve(EffectiveLlmSettings effective) {
        LlmProvider preset = LlmProvider.fromId(effective.getProvider());

        String baseUrl = firstNonBlank(effective.getBaseUrl(), preset.getDefaultBaseUrl());
        String apiKey = firstNonBlank(effective.getApiKey(), preset.getDefaultApiKeyPlaceholder());
        String model = firstNonBlank(effective.getModel(), preset.getDefaultModel());

        if (baseUrl == null || baseUrl.isBlank()) {
            throw new BusinessException("LLM baseUrl 未配置，请设置 provider 或 LLM_BASE_URL");
        }
        if (model == null || model.isBlank()) {
            throw new BusinessException("LLM model 未配置，请设置 LLM_MODEL（火山引擎需填 Endpoint ID）");
        }

        baseUrl = normalizeBaseUrl(baseUrl);
        externalAccessGuard.validateLlmEndpoint(baseUrl, effective.isAllowExternal());

        return ResolvedLlmConfig.builder()
                .provider(preset)
                .baseUrl(baseUrl)
                .apiKey(apiKey != null ? apiKey : "")
                .model(model)
                .maxTokens(effective.getMaxTokens())
                .temperature(effective.getTemperature())
                .allowExternal(effective.isAllowExternal())
                .build();
    }

    public ResolvedLlmConfig resolveFromRequest(String provider, String baseUrl, String apiKey, String model,
                                                boolean allowExternal) {
        String resolvedKey = llmSettingsService.resolveApiKeyForSave(apiKey);
        EffectiveLlmSettings effective = EffectiveLlmSettings.builder()
                .provider(provider)
                .baseUrl(baseUrl)
                .apiKey(resolvedKey)
                .model(model)
                .allowExternal(allowExternal)
                .maxTokens(properties.getLlm().getMaxTokens())
                .temperature(properties.getLlm().getTemperature())
                .build();
        return resolve(effective);
    }

    public List<Map<String, Object>> listProviderPresets() {
        return Arrays.stream(LlmProvider.values())
                .map(p -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", p.getId());
                    item.put("name", p.getDisplayName());
                    item.put("defaultBaseUrl", p.getDefaultBaseUrl());
                    item.put("defaultModel", p.getDefaultModel());
                    item.put("local", p.isLocal());
                    item.put("docsHint", docsHint(p));
                    return item;
                })
                .toList();
    }

    private String docsHint(LlmProvider provider) {
        return switch (provider) {
            case DEEPSEEK -> "https://platform.deepseek.com/api_keys";
            case GLM -> "https://open.bigmodel.cn/usercenter/apikeys";
            case DASHSCOPE -> "https://bailian.console.aliyun.com/";
            case VOLCENGINE -> "https://console.volcengine.com/ark — model 填推理接入点 ID";
            case OPENAI -> "https://platform.openai.com/api-keys";
            case OLLAMA -> "本地无需 API Key，可填 ollama";
            case CUSTOM -> "填写 OpenAI 兼容 API 的 baseUrl 与 model";
            default -> "";
        };
    }

    private String normalizeBaseUrl(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback;
    }
}
