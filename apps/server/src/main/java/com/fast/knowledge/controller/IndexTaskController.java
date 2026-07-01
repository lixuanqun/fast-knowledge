package com.fast.knowledge.controller;

import com.fast.knowledge.common.ApiResponse;
import com.fast.knowledge.model.entity.IndexTask;
import com.fast.knowledge.service.IndexRebuildService;
import com.fast.knowledge.service.IndexTaskService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/index-tasks")
public class IndexTaskController {

    private final IndexTaskService indexTaskService;
    private final IndexRebuildService indexRebuildService;

    public IndexTaskController(IndexTaskService indexTaskService, IndexRebuildService indexRebuildService) {
        this.indexTaskService = indexTaskService;
        this.indexRebuildService = indexRebuildService;
    }

    @GetMapping("/pending")
    public ApiResponse<List<IndexTask>> pending(@RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(indexTaskService.listPending(limit));
    }

    @GetMapping("/recent")
    public ApiResponse<List<IndexTask>> recent(@RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(indexTaskService.listRecent(limit));
    }

    @GetMapping("/failed")
    public ApiResponse<List<IndexTask>> failed(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) Long kbId) {
        if (kbId != null) {
            return ApiResponse.ok(indexTaskService.listFailedByKb(kbId, limit));
        }
        return ApiResponse.ok(indexTaskService.listFailed(limit));
    }

    @PostMapping("/{documentId}/retry")
    public ApiResponse<Void> retry(@PathVariable Long documentId) {
        indexTaskService.retry(documentId);
        return ApiResponse.ok();
    }

    @PostMapping("/rebuild/{kbId}")
    public ApiResponse<Map<String, Object>> rebuild(@PathVariable Long kbId) {
        indexRebuildService.requestRebuild(kbId);
        return ApiResponse.ok(Map.of("accepted", true, "message", "索引重建已在后台执行"));
    }
}
