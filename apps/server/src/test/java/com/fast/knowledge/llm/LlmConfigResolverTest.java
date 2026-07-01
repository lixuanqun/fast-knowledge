package com.fast.knowledge.llm;

import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.security.ExternalAccessGuard;
import com.fast.knowledge.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LlmConfigResolverTest {

    private KnowledgeProperties properties;
    private LlmConfigResolver resolver;

    @BeforeEach
    void setUp() {
        properties = new KnowledgeProperties();
        resolver = new LlmConfigResolver(properties, new ExternalAccessGuard(properties));
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
        assertThrows(BusinessException.class, resolver::resolve);
    }

    @Test
    void allowsLocalOllamaWhenExternalDisabled() {
        properties.getLlm().setProvider("ollama");
        properties.getLlm().setAllowExternal(false);
        ResolvedLlmConfig cfg = resolver.resolve();
        assertEquals("http://localhost:11434/v1", cfg.getBaseUrl());
    }

    @Test
    void listsAllPresetsExceptCustom() {
        assertEquals(6, resolver.listProviderPresets().size());
    }
}
