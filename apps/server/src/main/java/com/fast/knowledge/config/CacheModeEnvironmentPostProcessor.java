package com.fast.knowledge.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

/**
 * 缓存模式联动 — knowledge.cache.provider 非 redis（caffeine 单机模式）时关闭 Redis 健康检查，
 * 避免 /actuator/health 因 Redis 未部署而整体 DOWN。
 */
public class CacheModeEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String provider = environment.getProperty("knowledge.cache.provider", "redis");
        if (!"redis".equalsIgnoreCase(provider)) {
            environment.getPropertySources().addFirst(new MapPropertySource(
                    "cacheModeNoneOverrides",
                    Map.of("management.health.redis.enabled", false)));
        }
    }
}
