package com.fast.knowledge.llm;

import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.mapper.SystemConfigMapper;
import com.fast.knowledge.security.ExternalAccessGuard;
import com.fast.knowledge.service.LlmSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LlmConfigResolverTest {

    @Mock
    private SystemConfigMapper systemConfigMapper;

    private KnowledgeProperties properties;
    private LlmConfigResolver resolver;

    private final Map<String, String> store = new HashMap<>();

    @BeforeEach
    void setUp() {
        properties = new KnowledgeProperties();
        when(systemConfigMapper.getValue(anyString())).thenAnswer(inv -> store.get(inv.getArgument(0)));

        LlmSettingsService llmSettingsService = new LlmSettingsService(systemConfigMapper, properties);
        resolver = new LlmConfigResolver(properties, new ExternalAccessGuard(properties), llmSettingsService);
    }

    @Test
    void resolvesDeepSeekPreset() {
        properties.getLlm().setProvider("deepseek");
        properties.getLlm().setApiKey("sk-test");
        ResolvedLlmConfig cfg = resolver.resolve();
        assertEquals(LlmProvider.DEEPSEEK, cfg.getProvider());
        assertEquals("https://api.deepseek.com/v1", cfg.getBaseUrl());
        assertEquals("deepseek-chat", cfg.getModel());
    }

    @Test
    void resolvesDashScopePreset() {
        properties.getLlm().setProvider("dashscope");
        properties.getLlm().setApiKey("sk-test");
        ResolvedLlmConfig cfg = resolver.resolve();
        assertEquals("https://dashscope.aliyuncs.com/compatible-mode/v1", cfg.getBaseUrl());
        assertEquals("qwen-plus", cfg.getModel());
    }

    @Test
    void blocksExternalWhenDisabled() {
        properties.getLlm().setProvider("deepseek");
        properties.getLlm().setApiKey("sk-test");
        properties.getLlm().setBaseUrl("https://api.deepseek.com/v1");
        properties.getLlm().setModel("deepseek-chat");
        properties.getLlm().setAllowExternal(false);
        org.junit.jupiter.api.Assertions.assertThrows(
                com.fast.knowledge.common.BusinessException.class, resolver::resolve);
    }

    @Test
    void allowsLocalOllamaWhenExternalDisabled() {
        properties.getLlm().setProvider("ollama");
        properties.getLlm().setAllowExternal(false);
        ResolvedLlmConfig cfg = resolver.resolve();
        assertEquals("http://localhost:11434/v1", cfg.getBaseUrl());
    }

    @Test
    void dbOverridesEnv() {
        properties.getLlm().setProvider("ollama");
        store.put(LlmSettingsService.KEY_PROVIDER, "deepseek");
        store.put(LlmSettingsService.KEY_API_KEY, "sk-db");
        ResolvedLlmConfig cfg = resolver.resolve();
        assertEquals(LlmProvider.DEEPSEEK, cfg.getProvider());
    }

    @Test
    void listsAllPresetsIncludingCustom() {
        assertEquals(7, resolver.listProviderPresets().size());
    }
}
