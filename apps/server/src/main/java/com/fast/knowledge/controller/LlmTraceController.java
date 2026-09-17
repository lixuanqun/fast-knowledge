package com.fast.knowledge.controller;

import com.fast.knowledge.common.ApiResponse;
import com.fast.knowledge.mapper.LlmTraceMapper;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * WP10 LLM 用量看板 — 按场景/按日聚合，仅管理员。
 */
@RestController
@RequestMapping("/llm-traces")
@PreAuthorize("hasRole('ADMIN')")
public class LlmTraceController {

    private final LlmTraceMapper llmTraceMapper;

    public LlmTraceController(LlmTraceMapper llmTraceMapper) {
        this.llmTraceMapper = llmTraceMapper;
    }

    @GetMapping("/stats-by-scene")
    public ApiResponse<List<Map<String, Object>>> statsByScene(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since) {
        LocalDateTime from = since != null ? since : LocalDateTime.now().minusDays(7);
        return ApiResponse.ok(llmTraceMapper.statsByScene(from));
    }

    @GetMapping("/daily-stats")
    public ApiResponse<List<Map<String, Object>>> dailyStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since) {
        LocalDateTime from = since != null ? since : LocalDateTime.now().minusDays(7);
        return ApiResponse.ok(llmTraceMapper.dailyStats(from));
    }
}
