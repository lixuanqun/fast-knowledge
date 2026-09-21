package com.fast.knowledge.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * 基础设施健康检查联动 — 未启用的组件自动关闭对应健康指示器，
 * 避免 /actuator/health 因未部署的中间件而整体 DOWN：
 * <ul>
 *   <li>cache.provider ≠ redis（caffeine 单机模式）→ 关闭 redis</li>
 *   <li>auth.ldap.enabled = false → 关闭 ldap（classpath 上有 spring-ldap 时
 *       Boot 会自动装配 LDAP 健康指示器）</li>
 * </ul>
 */
public class InfraHealthGuardEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> overrides = new HashMap<>();
        String cacheProvider = environment.getProperty("knowledge.cache.provider", "redis");
        if (!"redis".equalsIgnoreCase(cacheProvider)) {
            overrides.put("management.health.redis.enabled", false);
        }
        boolean ldapEnabled = environment.getProperty("knowledge.auth.ldap.enabled", Boolean.class, false);
        if (!ldapEnabled) {
            overrides.put("management.health.ldap.enabled", false);
        }
        if (!overrides.isEmpty()) {
            environment.getPropertySources().addFirst(
                    new MapPropertySource("infraHealthGuardOverrides", overrides));
        }
    }
}
