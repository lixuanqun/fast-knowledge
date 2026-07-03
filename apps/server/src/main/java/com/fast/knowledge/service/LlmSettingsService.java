package com.fast.knowledge.service;

import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.llm.EffectiveLlmSettings;
import com.fast.knowledge.llm.LlmProvider;
import com.fast.knowledge.mapper.SystemConfigMapper;
import com.fast.knowledge.model.dto.LlmConfigRequest;
import com.fast.knowledge.model.vo.LlmConfigVO;
import org.springframework.stereotype.Service;

@Service
public class LlmSettingsService {

    public static final String KEY_PROVIDER = "llm.provider";
    public static final String KEY_BASE_URL = "llm.base_url";
    public static final String KEY_API_KEY = "llm.api_key";
    public static final String KEY_MODEL = "llm.model";
    public static final String KEY_ALLOW_EXTERNAL = "llm.allow_external";

    public static final String API_KEY_MASK = "********";
    public static final String API_KEY_MASK_PLACEHOLDER = API_KEY_MASK;

    private final SystemConfigMapper systemConfigMapper;
    private final KnowledgeProperties properties;

    public LlmSettingsService(SystemConfigMapper systemConfigMapper, KnowledgeProperties properties) {
        this.systemConfigMapper = systemConfigMapper;
        this.properties = properties;
    }

    public boolean hasStoredConfig() {
        String provider = systemConfigMapper.getValue(KEY_PROVIDER);
        return provider != null && !provider.isBlank();
    }

    public EffectiveLlmSettings getEffectiveLlm() {
        KnowledgeProperties.Llm env = properties.getLlm();
        return EffectiveLlmSettings.builder()
                .provider(firstNonBlank(systemConfigMapper.getValue(KEY_PROVIDER), env.getProvider()))
                .baseUrl(firstNonBlank(systemConfigMapper.getValue(KEY_BASE_URL), env.getBaseUrl()))
                .apiKey(firstNonBlank(systemConfigMapper.getValue(KEY_API_KEY), env.getApiKey()))
                .model(firstNonBlank(systemConfigMapper.getValue(KEY_MODEL), env.getModel()))
                .allowExternal(parseAllowExternal(systemConfigMapper.getValue(KEY_ALLOW_EXTERNAL), env.isAllowExternal()))
                .maxTokens(env.getMaxTokens())
                .temperature(env.getTemperature())
                .build();
    }

    public void save(LlmConfigRequest request) {
        systemConfigMapper.upsert(KEY_PROVIDER, request.getProvider().trim());
        systemConfigMapper.upsert(KEY_BASE_URL, nullToEmpty(request.getBaseUrl()).trim());
        systemConfigMapper.upsert(KEY_MODEL, nullToEmpty(request.getModel()).trim());
        systemConfigMapper.upsert(KEY_ALLOW_EXTERNAL, Boolean.toString(request.isAllowExternal()));

        String apiKey = request.getApiKey();
        if (!shouldKeepExistingApiKey(apiKey)) {
            systemConfigMapper.upsert(KEY_API_KEY, apiKey.trim());
        }
    }

    public LlmConfigVO toView(EffectiveLlmSettings effective, ResolvedLlmView resolved) {
        LlmConfigVO vo = new LlmConfigVO();
        vo.setProvider(effective.getProvider());
        vo.setProviderName(resolved.providerName());
        vo.setBaseUrl(resolved.baseUrl());
        vo.setModel(resolved.model());
        vo.setAllowExternal(effective.isAllowExternal());
        vo.setConfiguredInDb(hasStoredConfig());
        String storedKey = systemConfigMapper.getValue(KEY_API_KEY);
        vo.setApiKeyConfigured(storedKey != null && !storedKey.isBlank());
        vo.setApiKeyMask(maskApiKey(storedKey != null && !storedKey.isBlank()
                ? storedKey
                : effective.getApiKey()));
        return vo;
    }

    public String getStoredApiKey() {
        return systemConfigMapper.getValue(KEY_API_KEY);
    }

    public String resolveApiKeyForSave(String incoming) {
        if (shouldKeepExistingApiKey(incoming)) {
            String stored = getStoredApiKey();
            if (stored != null && !stored.isBlank()) {
                return stored;
            }
            return properties.getLlm().getApiKey() != null ? properties.getLlm().getApiKey() : "";
        }
        return incoming != null ? incoming.trim() : "";
    }

    public static boolean shouldKeepExistingApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return true;
        }
        return API_KEY_MASK.equals(apiKey) || API_KEY_MASK_PLACEHOLDER.equals(apiKey);
    }

    public static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "";
        }
        if (apiKey.length() <= 8) {
            return API_KEY_MASK;
        }
        return apiKey.substring(0, 4) + "..." + apiKey.substring(apiKey.length() - 4);
    }

    public record ResolvedLlmView(String providerName, String baseUrl, String model) {
        public static ResolvedLlmView from(EffectiveLlmSettings effective) {
            LlmProvider preset = LlmProvider.fromId(effective.getProvider());
            String baseUrl = firstNonBlank(effective.getBaseUrl(), preset.getDefaultBaseUrl());
            String model = firstNonBlank(effective.getModel(), preset.getDefaultModel());
            return new ResolvedLlmView(preset.getDisplayName(), baseUrl, model);
        }
    }

    private static boolean parseAllowExternal(String dbValue, boolean envDefault) {
        if (dbValue == null || dbValue.isBlank()) {
            return envDefault;
        }
        return Boolean.parseBoolean(dbValue);
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback != null ? fallback : "";
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
