package com.fast.knowledge.llm;

import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.config.KnowledgeProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class LlmConfigResolver {

    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "ollama", "host.docker.internal");

    private final KnowledgeProperties properties;

    public LlmConfigResolver(KnowledgeProperties properties) {
        this.properties = properties;
    }

    public ResolvedLlmConfig resolve() {
        KnowledgeProperties.Llm llm = properties.getLlm();
        LlmProvider preset = LlmProvider.fromId(llm.getProvider());

        String baseUrl = firstNonBlank(llm.getBaseUrl(), preset.getDefaultBaseUrl());
        String apiKey = firstNonBlank(llm.getApiKey(), preset.getDefaultApiKeyPlaceholder());
        String model = firstNonBlank(llm.getModel(), preset.getDefaultModel());

        if (baseUrl == null || baseUrl.isBlank()) {
            throw new BusinessException("LLM baseUrl 未配置，请设置 knowledge.llm.provider 或 LLM_BASE_URL");
        }
        if (model == null || model.isBlank()) {
            throw new BusinessException("LLM model 未配置，请设置 LLM_MODEL（火山引擎需填 Endpoint ID）");
        }

        baseUrl = normalizeBaseUrl(baseUrl);
        validateExternalAccess(baseUrl, llm.isAllowExternal());

        return ResolvedLlmConfig.builder()
                .provider(preset)
                .baseUrl(baseUrl)
                .apiKey(apiKey != null ? apiKey : "")
                .model(model)
                .maxTokens(llm.getMaxTokens())
                .temperature(llm.getTemperature())
                .allowExternal(llm.isAllowExternal())
                .build();
    }

    public List<Map<String, Object>> listProviderPresets() {
        return Arrays.stream(LlmProvider.values())
                .filter(p -> p != LlmProvider.CUSTOM)
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

    private void validateExternalAccess(String baseUrl, boolean allowExternal) {
        if (allowExternal || isLocalUrl(baseUrl)) {
            return;
        }
        throw new BusinessException(
                "已禁止外连大模型（knowledge.llm.allow-external=false），当前 baseUrl 非本地: " + baseUrl);
    }

    private boolean isLocalUrl(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl);
            String host = uri.getHost();
            if (host == null) {
                return true;
            }
            return LOCAL_HOSTS.contains(host.toLowerCase(Locale.ROOT));
        } catch (Exception e) {
            return false;
        }
    }

    private String docsHint(LlmProvider provider) {
        return switch (provider) {
            case DEEPSEEK -> "https://platform.deepseek.com/api_keys";
            case GLM -> "https://open.bigmodel.cn/usercenter/apikeys";
            case DASHSCOPE -> "https://bailian.console.aliyun.com/";
            case VOLCENGINE -> "https://console.volcengine.com/ark — model 填推理接入点 ID";
            case OPENAI -> "https://platform.openai.com/api-keys";
            case OLLAMA -> "本地无需 API Key，可填 ollama";
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
