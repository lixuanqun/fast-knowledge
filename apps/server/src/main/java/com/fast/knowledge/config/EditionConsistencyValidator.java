package com.fast.knowledge.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 社区版误开企业能力时启动失败，避免「看似启用却无授权」的配置漂移。
 */
@Component
public class EditionConsistencyValidator {

    private static final Logger log = LoggerFactory.getLogger(EditionConsistencyValidator.class);

    private final KnowledgeProperties properties;

    public EditionConsistencyValidator(KnowledgeProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void validate() {
        String edition = properties.getEdition() != null ? properties.getEdition() : "community";
        log.info("Knowledge edition: {}", edition);
        if (properties.isEnterprise()) {
            return;
        }
        if (properties.getAuth().getLdap().isEnabled()) {
            throw new IllegalStateException(
                    "community 版不能启用 LDAP。请设置 knowledge.edition=enterprise，或关闭 knowledge.auth.ldap.enabled");
        }
        if (properties.getAuth().getOidc().isEnabled()) {
            throw new IllegalStateException(
                    "community 版不能启用 OIDC。请设置 knowledge.edition=enterprise，或关闭 knowledge.auth.oidc.enabled");
        }
    }
}
