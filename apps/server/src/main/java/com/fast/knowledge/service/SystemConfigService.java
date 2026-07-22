package com.fast.knowledge.service;

import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.llm.EffectiveLlmSettings;
import com.fast.knowledge.llm.LlmConfigResolver;
import com.fast.knowledge.llm.ResolvedLlmConfig;
import com.fast.knowledge.mapper.SystemConfigMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class SystemConfigService {

    public static final String KEY_SETUP_COMPLETE = "setup_complete";
    public static final String KEY_INSTANCE_NAME = "instance_name";

    private final SystemConfigMapper systemConfigMapper;
    private final KnowledgeProperties properties;
    private final LlmSettingsService llmSettingsService;
    private final LlmConfigResolver llmConfigResolver;

    public SystemConfigService(SystemConfigMapper systemConfigMapper,
                               KnowledgeProperties properties,
                               LlmSettingsService llmSettingsService,
                               LlmConfigResolver llmConfigResolver) {
        this.systemConfigMapper = systemConfigMapper;
        this.properties = properties;
        this.llmSettingsService = llmSettingsService;
        this.llmConfigResolver = llmConfigResolver;
    }

    public boolean isSetupComplete() {
        return "true".equalsIgnoreCase(systemConfigMapper.getValue(KEY_SETUP_COMPLETE));
    }

    public void markSetupComplete() {
        systemConfigMapper.upsert(KEY_SETUP_COMPLETE, "true");
    }

    public String getInstanceName() {
        String name = systemConfigMapper.getValue(KEY_INSTANCE_NAME);
        return name != null ? name : properties.getSetup().getInstanceName();
    }

    public void setInstanceName(String name) {
        systemConfigMapper.upsert(KEY_INSTANCE_NAME, name);
    }

    public Map<String, Object> getPublicConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("instanceName", getInstanceName());
        config.put("setupComplete", isSetupComplete());
        config.put("vectorProvider", properties.getVector().getProvider());
        config.put("embeddingProvider", properties.getEmbedding().getProvider());

        EffectiveLlmSettings effective = llmSettingsService.getEffectiveLlm();
        ResolvedLlmConfig resolved = llmConfigResolver.resolve(effective);
        config.put("llmProvider", effective.getProvider());
        config.put("llmProviderName", resolved.getProvider().getDisplayName());
        config.put("llmModel", resolved.getModel());
        config.put("llmAllowExternal", effective.isAllowExternal());
        config.put("rerankEnabled", properties.getSearch().getRerank().isEnabled());
        config.put("rerankProvider", properties.getSearch().getRerank().getProvider());
        config.put("ldapEnabled", properties.isEnterprise() && properties.getAuth().getLdap().isEnabled());
        config.put("oidcEnabled", properties.isEnterprise() && properties.getAuth().getOidc().isEnabled());
        config.put("edition", properties.getEdition() != null ? properties.getEdition() : "community");
        config.put("enterprise", properties.isEnterprise());
        return config;
    }
}
