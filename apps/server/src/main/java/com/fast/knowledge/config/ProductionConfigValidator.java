package com.fast.knowledge.config;

import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 当通过环境变量注入 JWT_SECRET 时校验密钥强度（K8s 生产部署）。
 */
@Component
public class ProductionConfigValidator {

    private static final Set<String> WEAK_JWT_SECRETS = Set.of(
            "fast-knowledge-jwt-secret-change-in-production-32chars",
            "fast-knowledge-docker-jwt-secret-change-me-32c"
    );

    private static final Set<String> WEAK_MINIO_KEYS = Set.of("minioadmin", "minio", "admin");

    private static final int MIN_JWT_SECRET_LENGTH = 32;

    private final KnowledgeProperties properties;
    private final Environment environment;

    public ProductionConfigValidator(KnowledgeProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @PostConstruct
    public void validateOnStartup() {
        if (environment.getProperty("JWT_SECRET") != null) {
            validateJwtSecret();
        }
        if (isProductionLikeDeploy()) {
            validateMinioCredentials();
            validatePrivacyMode();
        }
    }

    private boolean isProductionLikeDeploy() {
        String profiles = environment.getProperty("SPRING_PROFILES_ACTIVE", "");
        return environment.getProperty("JWT_SECRET") != null
                || environment.getProperty("MINIO_ACCESS_KEY") != null
                || profiles.contains("enterprise");
    }

    private void validateMinioCredentials() {
        String accessKey = properties.getStorage().getMinio().getAccessKey();
        if (accessKey != null && WEAK_MINIO_KEYS.contains(accessKey.toLowerCase())) {
            throw new IllegalStateException("生产环境 MinIO ACCESS_KEY 不得使用默认值 minioadmin，请更换");
        }
    }

    private void validatePrivacyMode() {
        if (!properties.getLlm().isAllowExternal()) {
            String rerankProvider = properties.getSearch().getRerank().getProvider();
            if (properties.getSearch().getRerank().isEnabled()
                    && rerankProvider != null
                    && !"onnx".equalsIgnoreCase(rerankProvider.trim())) {
                throw new IllegalStateException(
                        "allow-external=false 时 Rerank 仅允许 provider=onnx，当前: " + rerankProvider);
            }
        }
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
    }
}
