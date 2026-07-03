package com.fast.knowledge.controller;

import com.fast.knowledge.common.ApiResponse;
import com.fast.knowledge.llm.EffectiveLlmSettings;
import com.fast.knowledge.llm.LlmConfigResolver;
import com.fast.knowledge.llm.LlmModelRegistry;
import com.fast.knowledge.llm.ResolvedLlmConfig;
import com.fast.knowledge.model.dto.LlmConfigRequest;
import com.fast.knowledge.model.vo.LlmConfigVO;
import com.fast.knowledge.service.LlmSettingsService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/system")
@PreAuthorize("hasRole('ADMIN')")
public class LlmConfigController {

    private final LlmSettingsService llmSettingsService;
    private final LlmConfigResolver llmConfigResolver;
    private final LlmModelRegistry llmModelRegistry;

    public LlmConfigController(LlmSettingsService llmSettingsService,
                               LlmConfigResolver llmConfigResolver,
                               LlmModelRegistry llmModelRegistry) {
        this.llmSettingsService = llmSettingsService;
        this.llmConfigResolver = llmConfigResolver;
        this.llmModelRegistry = llmModelRegistry;
    }

    @GetMapping("/llm-config")
    public ApiResponse<LlmConfigVO> getConfig() {
        EffectiveLlmSettings effective = llmSettingsService.getEffectiveLlm();
        ResolvedLlmConfig resolved = llmConfigResolver.resolve(effective);
        LlmSettingsService.ResolvedLlmView view = new LlmSettingsService.ResolvedLlmView(
                resolved.getProvider().getDisplayName(),
                resolved.getBaseUrl(),
                resolved.getModel()
        );
        return ApiResponse.ok(llmSettingsService.toView(effective, view));
    }

    @PutMapping("/llm-config")
    public ApiResponse<LlmConfigVO> updateConfig(@Valid @RequestBody LlmConfigRequest request) {
        llmSettingsService.save(request);
        llmModelRegistry.refresh();
        return getConfig();
    }

    @PostMapping("/llm-config/test")
    public ApiResponse<Map<String, String>> testConfig(@Valid @RequestBody LlmConfigRequest request) {
        ResolvedLlmConfig cfg = llmConfigResolver.resolveFromRequest(
                request.getProvider(),
                request.getBaseUrl(),
                request.getApiKey(),
                request.getModel(),
                request.isAllowExternal()
        );
        String message = llmModelRegistry.testConnectivity(cfg);
        return ApiResponse.ok(Map.of("message", message));
    }
}
