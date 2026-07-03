package com.fast.knowledge.config;

import com.fast.knowledge.mapper.UserMapper;
import com.fast.knowledge.model.entity.KbUser;
import com.fast.knowledge.service.SystemConfigService;
import com.fast.knowledge.service.WorkspaceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DataInitializer implements CommandLineRunner {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final WorkspaceService workspaceService;
    private final SystemConfigService systemConfigService;

    public DataInitializer(UserMapper userMapper, PasswordEncoder passwordEncoder,
                           WorkspaceService workspaceService, SystemConfigService systemConfigService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.workspaceService = workspaceService;
        this.systemConfigService = systemConfigService;
    }

    @Override
    public void run(String... args) {
        if (userMapper.countAll() == 0) {
            KbUser admin = new KbUser();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setDisplayName("系统管理员");
            admin.setRole("ADMIN");
            admin.setStatus(1);
            admin.setMustChangePassword(1);
            admin.setAuthSource("LOCAL");
            userMapper.insert(admin);
            workspaceService.createDefaultForUser(admin.getId());
            log.info("已创建默认管理员账号: admin / admin123（首次登录须修改密码）");
        }
        if (workspaceService.getDefault() == null) {
            KbUser admin = userMapper.findByUsername("admin");
            if (admin != null) {
                workspaceService.createDefaultForUser(admin.getId());
            }
        }
        if (!systemConfigService.isSetupComplete()) {
            systemConfigService.setInstanceName(systemConfigService.getInstanceName());
        }
    }
}
