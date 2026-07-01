package com.fast.knowledge.service;

import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.llm.LlmProvider;
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

    public SystemConfigService(SystemConfigMapper systemConfigMapper, KnowledgeProperties properties) {
        this.systemConfigMapper = systemConfigMapper;
        this.properties = properties;
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
        config.put("llmProvider", properties.getLlm().getProvider());
        config.put("llmProviderName", LlmProvider.fromId(properties.getLlm().getProvider()).getDisplayName());
        config.put("llmModel", properties.getLlm().getModel());
        config.put("llmAllowExternal", properties.getLlm().isAllowExternal());
        return config;
    }
}
