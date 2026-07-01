package com.fast.knowledge.controller;

import com.fast.knowledge.common.ApiResponse;
import com.fast.knowledge.llm.LlmConfigResolver;
import com.fast.knowledge.service.SystemConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/system")
public class SystemController {

    private final SystemConfigService systemConfigService;
    private final LlmConfigResolver llmConfigResolver;

    public SystemController(SystemConfigService systemConfigService, LlmConfigResolver llmConfigResolver) {
        this.systemConfigService = systemConfigService;
        this.llmConfigResolver = llmConfigResolver;
    }

    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> getPublicConfig() {
        return ApiResponse.ok(systemConfigService.getPublicConfig());
    }

    @GetMapping("/llm-providers")
    public ApiResponse<List<Map<String, Object>>> listLlmProviders() {
        return ApiResponse.ok(llmConfigResolver.listProviderPresets());
    }
}
