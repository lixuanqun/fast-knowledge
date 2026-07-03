package com.fast.knowledge.security;

import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.config.KnowledgeProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * 校验 HTTP 端点是否允许在非本地环境访问（配合 knowledge.llm.allow-external）。
 */
@Component
public class ExternalAccessGuard {

    static final Set<String> LOCAL_HOSTS = Set.of(
            "localhost", "127.0.0.1", "0.0.0.0", "ollama", "host.docker.internal"
    );

    private final KnowledgeProperties properties;

    public ExternalAccessGuard(KnowledgeProperties properties) {
        this.properties = properties;
    }

    public void validateLlmEndpoint(String baseUrl) {
        validateHttpEndpoint(baseUrl, properties.getLlm().isAllowExternal(), "大模型");
    }

    public void validateLlmEndpoint(String baseUrl, boolean allowExternal) {
        validateHttpEndpoint(baseUrl, allowExternal, "大模型");
    }

    public void validateEmbeddingEndpoint(String baseUrl) {
        validateHttpEndpoint(baseUrl, "Embedding");
    }

    public void validateHttpEndpoint(String baseUrl, String serviceLabel) {
        validateHttpEndpoint(baseUrl, properties.getLlm().isAllowExternal(), serviceLabel);
    }

    public void validateHttpEndpoint(String baseUrl, boolean allowExternal, String serviceLabel) {
        if (allowExternal || isLocalUrl(baseUrl)) {
            return;
        }
        throw new BusinessException(
                "已禁止外连（knowledge.llm.allow-external=false），" + serviceLabel + " 端点非本地: " + baseUrl);
    }

    public boolean isLocalUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return true;
        }
        try {
            URI uri = URI.create(baseUrl.trim());
            String host = uri.getHost();
            if (host == null) {
                return true;
            }
            String lower = host.toLowerCase(Locale.ROOT);
            if (LOCAL_HOSTS.contains(lower)) {
                return true;
            }
            return lower.endsWith(".local") || lower.endsWith(".internal");
        } catch (Exception e) {
            return false;
        }
    }
}
