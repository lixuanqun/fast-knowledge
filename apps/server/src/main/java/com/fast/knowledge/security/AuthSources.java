package com.fast.knowledge.security;

/**
 * 用户认证来源。
 */
public final class AuthSources {

    public static final String LOCAL = "LOCAL";
    public static final String LDAP = "LDAP";
    public static final String OIDC = "OIDC";

    private AuthSources() {
    }
}
