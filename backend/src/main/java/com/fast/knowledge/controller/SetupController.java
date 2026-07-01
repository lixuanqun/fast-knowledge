package com.fast.knowledge.controller;

import com.fast.knowledge.common.ApiResponse;
import com.fast.knowledge.model.dto.SetupRequest;
import com.fast.knowledge.security.UserContext;
import com.fast.knowledge.service.SystemConfigService;
import com.fast.knowledge.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class SetupController {

    private final UserService userService;
    private final SystemConfigService systemConfigService;

    public SetupController(UserService userService, SystemConfigService systemConfigService) {
        this.userService = userService;
        this.systemConfigService = systemConfigService;
    }

    @PostMapping("/setup")
    public ApiResponse<Void> completeSetup(@Valid @RequestBody SetupRequest request) {
        userService.completeInitialSetup(UserContext.currentUserId(), request.getNewPassword());
        systemConfigService.setInstanceName(request.getInstanceName());
        systemConfigService.markSetupComplete();
        return ApiResponse.ok();
    }
}
