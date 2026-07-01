package com.fast.knowledge.controller;

import com.fast.knowledge.common.ApiResponse;
import com.fast.knowledge.model.dto.KnowledgeBaseRequest;
import com.fast.knowledge.model.entity.KnowledgeBase;
import com.fast.knowledge.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/kbs")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @GetMapping
    public ApiResponse<List<KnowledgeBase>> list() {
        return ApiResponse.ok(knowledgeBaseService.listMine());
    }

    @GetMapping("/{id}")
    public ApiResponse<KnowledgeBase> get(@PathVariable Long id) {
        return ApiResponse.ok(knowledgeBaseService.getById(id));
    }

    @PostMapping
    public ApiResponse<KnowledgeBase> create(@Valid @RequestBody KnowledgeBaseRequest request) {
        return ApiResponse.ok(knowledgeBaseService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<KnowledgeBase> update(@PathVariable Long id,
                                           @Valid @RequestBody KnowledgeBaseRequest request) {
        return ApiResponse.ok(knowledgeBaseService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        knowledgeBaseService.delete(id);
        return ApiResponse.ok();
    }
}
