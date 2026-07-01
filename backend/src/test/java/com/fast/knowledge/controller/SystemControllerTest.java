package com.fast.knowledge.controller;

import com.fast.knowledge.llm.LlmConfigResolver;
import com.fast.knowledge.service.SystemConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemControllerTest {

    @Mock
    private SystemConfigService systemConfigService;

    @Mock
    private LlmConfigResolver llmConfigResolver;

    @InjectMocks
    private SystemController systemController;

    @Test
    void getPublicConfig() {
        when(systemConfigService.getPublicConfig()).thenReturn(Map.of(
                "instanceName", "Test",
                "setupComplete", false
        ));
        var response = systemController.getPublicConfig();
        assertEquals("Test", response.getData().get("instanceName"));
    }

    @Test
    void listLlmProviders() {
        when(llmConfigResolver.listProviderPresets()).thenReturn(List.of(
                Map.of("id", "deepseek", "name", "DeepSeek")
        ));
        var response = systemController.listLlmProviders();
        assertFalse(response.getData().isEmpty());
        assertEquals("deepseek", response.getData().get(0).get("id"));
    }
}
