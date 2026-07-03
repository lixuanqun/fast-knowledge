package com.fast.knowledge.service;

import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.mapper.SystemConfigMapper;
import com.fast.knowledge.model.dto.LlmConfigRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LlmSettingsServiceTest {

    @Mock
    private SystemConfigMapper systemConfigMapper;

    private KnowledgeProperties properties;
    private LlmSettingsService service;

    private final Map<String, String> store = new HashMap<>();

    @BeforeEach
    void setUp() {
        properties = new KnowledgeProperties();
        properties.getLlm().setProvider("ollama");
        properties.getLlm().setBaseUrl("http://localhost:11434/v1");
        properties.getLlm().setApiKey("ollama");
        properties.getLlm().setModel("qwen2.5:7b");

        when(systemConfigMapper.getValue(anyString())).thenAnswer(inv -> store.get(inv.getArgument(0)));
        doAnswer(inv -> {
            store.put(inv.getArgument(0), inv.getArgument(1));
            return 1;
        }).when(systemConfigMapper).upsert(anyString(), anyString());

        service = new LlmSettingsService(systemConfigMapper, properties);
    }

    @Test
    void effectiveLlmFallsBackToEnvWhenDbEmpty() {
        var effective = service.getEffectiveLlm();
        assertEquals("ollama", effective.getProvider());
        assertEquals("http://localhost:11434/v1", effective.getBaseUrl());
        assertEquals("qwen2.5:7b", effective.getModel());
        assertFalse(service.hasStoredConfig());
    }

    @Test
    void effectiveLlmPrefersDbOverEnv() {
        store.put(LlmSettingsService.KEY_PROVIDER, "deepseek");
        store.put(LlmSettingsService.KEY_MODEL, "deepseek-chat");
        store.put(LlmSettingsService.KEY_API_KEY, "sk-db");

        var effective = service.getEffectiveLlm();
        assertEquals("deepseek", effective.getProvider());
        assertEquals("deepseek-chat", effective.getModel());
        assertEquals("sk-db", effective.getApiKey());
        assertTrue(service.hasStoredConfig());
    }

    @Test
    void saveKeepsExistingApiKeyWhenPlaceholder() {
        store.put(LlmSettingsService.KEY_API_KEY, "sk-existing");

        LlmConfigRequest request = new LlmConfigRequest();
        request.setProvider("deepseek");
        request.setBaseUrl("https://api.deepseek.com/v1");
        request.setModel("deepseek-chat");
        request.setApiKey(LlmSettingsService.API_KEY_MASK);
        request.setAllowExternal(true);

        service.save(request);

        verify(systemConfigMapper, never()).upsert(eq(LlmSettingsService.KEY_API_KEY), anyString());
        assertEquals("sk-existing", store.get(LlmSettingsService.KEY_API_KEY));
    }

    @Test
    void saveUpdatesApiKeyWhenProvided() {
        LlmConfigRequest request = new LlmConfigRequest();
        request.setProvider("deepseek");
        request.setBaseUrl("https://api.deepseek.com/v1");
        request.setModel("deepseek-chat");
        request.setApiKey("sk-new");
        request.setAllowExternal(true);

        service.save(request);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(systemConfigMapper).upsert(eq(LlmSettingsService.KEY_API_KEY), keyCaptor.capture());
        assertEquals("sk-new", keyCaptor.getValue());
    }

    @Test
    void maskApiKey() {
        assertEquals("", LlmSettingsService.maskApiKey(null));
        assertEquals("sk-a...mnop", LlmSettingsService.maskApiKey("sk-abcdefghijklmnop"));
        assertEquals(LlmSettingsService.API_KEY_MASK, LlmSettingsService.maskApiKey("short"));
    }
}
