package com.fast.knowledge.controller;

import com.fast.knowledge.common.ApiResponse;
import com.fast.knowledge.model.entity.AuditLog;
import com.fast.knowledge.model.vo.RagOpsVO;
import com.fast.knowledge.security.UserContext;
import org.springframework.security.access.prepost.PreAuthorize;
import com.fast.knowledge.service.AuditLogService;
import com.fast.knowledge.service.DashboardService;
import com.fast.knowledge.service.RagOpsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final AuditLogService auditLogService;
    private final RagOpsService ragOpsService;

    public DashboardController(DashboardService dashboardService,
                               AuditLogService auditLogService,
                               RagOpsService ragOpsService) {
        this.dashboardService = dashboardService;
        this.auditLogService = auditLogService;
        this.ragOpsService = ragOpsService;
    }

    @GetMapping("/stats")
    public ApiResponse<DashboardService.DashboardVO> stats() {
        return ApiResponse.ok(dashboardService.stats(UserContext.currentUserId()));
    }

    @GetMapping("/audits")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<AuditLog>> audits(@RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(auditLogService.recent(Math.min(limit, 200)));
    }

    @GetMapping("/rag-ops")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<RagOpsVO> ragOps() {
        return ApiResponse.ok(ragOpsService.snapshot());
    }
}
