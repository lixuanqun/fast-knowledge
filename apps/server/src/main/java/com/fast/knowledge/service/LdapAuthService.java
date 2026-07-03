package com.fast.knowledge.service;

import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.config.KnowledgeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.stereotype.Service;

import javax.naming.directory.DirContext;
import java.util.Hashtable;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "knowledge.auth.ldap", name = "enabled", havingValue = "true")
public class LdapAuthService {

    private final KnowledgeProperties properties;
    private final LdapContextSource contextSource;

    public LdapAuthService(KnowledgeProperties properties) {
        this.properties = properties;
        this.contextSource = buildContextSource(properties.getAuth().getLdap());
    }

    public boolean authenticate(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return false;
        }
        KnowledgeProperties.Ldap ldap = properties.getAuth().getLdap();
        String userDn = resolveUserDn(username, ldap);
        DirContext ctx = null;
        try {
            ctx = contextSource.getContext(userDn, password);
            return true;
        } catch (Exception e) {
            log.debug("LDAP bind failed for {}: {}", username, e.getMessage());
            return false;
        } finally {
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public String resolveDisplayName(String username) {
        return username;
    }

    private String resolveUserDn(String username, KnowledgeProperties.Ldap ldap) {
        if (ldap.getUserDnPattern() != null && !ldap.getUserDnPattern().isBlank()) {
            return ldap.getUserDnPattern().replace("{0}", username);
        }
        throw new BusinessException("LDAP 未配置 user-dn-pattern");
    }

    private static LdapContextSource buildContextSource(KnowledgeProperties.Ldap ldap) {
        if (ldap.getUrl() == null || ldap.getUrl().isBlank()) {
            throw new IllegalStateException("knowledge.auth.ldap.url 未配置");
        }
        LdapContextSource source = new LdapContextSource();
        source.setUrl(ldap.getUrl());
        if (ldap.getBaseDn() != null && !ldap.getBaseDn().isBlank()) {
            source.setBase(ldap.getBaseDn());
        }
        Hashtable<String, Object> env = new Hashtable<>();
        env.put("com.sun.jndi.ldap.connect.timeout", "5000");
        env.put("com.sun.jndi.ldap.read.timeout", "5000");
        source.setBaseEnvironmentProperties(env);
        source.afterPropertiesSet();
        return source;
    }
}
