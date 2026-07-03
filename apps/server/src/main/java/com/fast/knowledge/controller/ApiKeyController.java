package com.fast.knowledge.controller;

import com.fast.knowledge.common.ApiResponse;
import com.fast.knowledge.model.dto.CreateApiKeyRequest;
import com.fast.knowledge.model.vo.ApiKeyCreatedVO;
import com.fast.knowledge.model.vo.ApiKeyVO;
import com.fast.knowledge.service.ApiKeyService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api-keys")
@PreAuthorize("hasRole('ADMIN')")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @GetMapping
    public ApiResponse<List<ApiKeyVO>> list() {
        return ApiResponse.ok(apiKeyService.list());
    }

    @PostMapping
    public ApiResponse<ApiKeyCreatedVO> create(@Valid @RequestBody CreateApiKeyRequest request) {
        return ApiResponse.ok(apiKeyService.create(request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> revoke(@PathVariable Long id) {
        apiKeyService.revoke(id);
        return ApiResponse.ok();
    }
}
