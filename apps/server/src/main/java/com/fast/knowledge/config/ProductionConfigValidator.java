package com.fast.knowledge.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 生产环境启动时校验关键安全配置，防止使用弱默认值上线。
 */
@Component
@Profile("prod")
public class ProductionConfigValidator {

    private static final Set<String> WEAK_JWT_SECRETS = Set.of(
            "fast-knowledge-jwt-secret-change-in-production-32chars",
            "fast-knowledge-docker-jwt-secret-change-me-32c"
    );

    private static final int MIN_JWT_SECRET_LENGTH = 32;

    private final KnowledgeProperties properties;
    private final Environment environment;

    public ProductionConfigValidator(KnowledgeProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @PostConstruct
    public void validateOnStartup() {
        validateJwtSecret();
    }

    private void validateJwtSecret() {
        String secret = properties.getJwt().getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("生产环境必须设置 JWT_SECRET 环境变量（≥32 字符）");
        }
        if (secret.length() < MIN_JWT_SECRET_LENGTH) {
            throw new IllegalStateException("JWT_SECRET 长度不足，生产环境要求至少 " + MIN_JWT_SECRET_LENGTH + " 字符");
        }
        if (WEAK_JWT_SECRETS.contains(secret)) {
            throw new IllegalStateException("JWT_SECRET 使用了已知弱默认值，生产环境必须更换");
        }
        if (environment.getProperty("JWT_SECRET") == null) {
            throw new IllegalStateException("生产环境必须通过环境变量 JWT_SECRET 注入密钥，不可依赖配置文件默认值");
        }
    }
}
