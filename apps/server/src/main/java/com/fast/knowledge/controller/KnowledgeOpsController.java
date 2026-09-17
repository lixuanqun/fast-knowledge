package com.fast.knowledge.controller;

import com.fast.knowledge.common.ApiResponse;
import com.fast.knowledge.model.entity.KbDocument;
import com.fast.knowledge.model.entity.KbGapQuery;
import com.fast.knowledge.service.KnowledgeOpsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * WP9 主动知识运营 — 缺口采集 / 过期扫描 / 冲突检测（仅管理员）。
 */
@RestController
@RequestMapping("/knowledge-ops")
@PreAuthorize("hasRole('ADMIN')")
public class KnowledgeOpsController {

    private final KnowledgeOpsService knowledgeOpsService;

    public KnowledgeOpsController(KnowledgeOpsService knowledgeOpsService) {
        this.knowledgeOpsService = knowledgeOpsService;
    }

    @GetMapping("/gaps")
    public ApiResponse<List<KbGapQuery>> gaps(@RequestParam(required = false) Long kbId,
                                              @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(knowledgeOpsService.listGaps(kbId, limit));
    }

    @PutMapping("/gaps/{id}")
    public ApiResponse<Void> resolveGap(@PathVariable Long id,
                                        @RequestParam(defaultValue = "ADDRESSED") String status) {
        knowledgeOpsService.resolveGap(id, status);
        return ApiResponse.ok(null);
    }

    @GetMapping("/expired-docs")
    public ApiResponse<List<KbDocument>> expiredDocs(@RequestParam(required = false) Long kbId) {
        return ApiResponse.ok(knowledgeOpsService.listExpiredDocs(kbId));
    }

    @GetMapping("/conflicts")
    public ApiResponse<List<Map<String, Object>>> conflicts(@RequestParam(required = false) Long kbId) {
        return ApiResponse.ok(knowledgeOpsService.listConflicts(kbId));
    }
}
