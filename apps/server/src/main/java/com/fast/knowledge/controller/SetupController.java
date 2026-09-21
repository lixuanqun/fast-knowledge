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

    /**
     * 首启初始化。安全模型与主流自部署产品一致：
     * <ul>
     *   <li>初始化未完成（首启窗口期）：允许匿名调用，定位 DataInitializer 创建的唯一初始管理员设置密码，
     *       完成后 {@code markSetupComplete} 立即关闭匿名窗口；</li>
     *   <li>初始化已完成：接口收紧为登录态（强制改密流程复用同一路径）。</li>
     * </ul>
     */
    @PostMapping("/setup")
    public ApiResponse<Void> completeSetup(@Valid @RequestBody SetupRequest request) {
        if (systemConfigService.isSetupComplete()) {
            userService.completeInitialSetup(UserContext.currentUserId(), request.getNewPassword());
        } else {
            userService.completeInitialSetupForInitialAdmin(request.getNewPassword());
        }
        systemConfigService.setInstanceName(request.getInstanceName());
        systemConfigService.markSetupComplete();
        return ApiResponse.ok();
    }
}
