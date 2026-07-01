package com.fast.knowledge.controller;

import com.fast.knowledge.common.ApiResponse;
import com.fast.knowledge.model.entity.AuditLog;
import com.fast.knowledge.security.UserContext;
import com.fast.knowledge.service.AuditLogService;
import com.fast.knowledge.service.DashboardService;
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

    public DashboardController(DashboardService dashboardService, AuditLogService auditLogService) {
        this.dashboardService = dashboardService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/stats")
    public ApiResponse<DashboardService.DashboardVO> stats() {
        return ApiResponse.ok(dashboardService.stats(UserContext.currentUserId()));
    }

    @GetMapping("/audits")
    public ApiResponse<List<AuditLog>> audits(@RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(auditLogService.recent(limit));
    }
}
