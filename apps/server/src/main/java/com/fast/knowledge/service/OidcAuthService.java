package com.fast.knowledge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fast.knowledge.cache.CacheProvider;
import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.config.KnowledgeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "knowledge.auth.oidc", name = "enabled", havingValue = "true")
public class OidcAuthService {

    private static final String STATE_PREFIX = "oidc:state:";

    private final KnowledgeProperties properties;
    private final CacheProvider cacheProvider;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private volatile OidcEndpoints endpoints;

    public OidcAuthService(KnowledgeProperties properties,
                           CacheProvider cacheProvider,
                           ObjectMapper objectMapper) {
        this.properties = properties;
        this.cacheProvider = cacheProvider;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
    }

    public String buildAuthorizationUrl() {
        KnowledgeProperties.Oidc oidc = properties.getAuth().getOidc();
        validateOidcConfig(oidc);
        OidcEndpoints ep = loadEndpoints(oidc.getIssuerUri());
        String state = UUID.randomUUID().toString().replace("-", "");
        cacheProvider.set(STATE_PREFIX + state, "1", Duration.ofMinutes(10));
        return UriComponentsBuilder.fromUriString(ep.authorizationEndpoint())
                .queryParam("response_type", "code")
                .queryParam("client_id", oidc.getClientId())
                .queryParam("redirect_uri", oidc.getRedirectUri())
                .queryParam("scope", oidc.getScope())
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    public OidcUserInfo exchangeCode(String code, String state) {
        if (state == null || state.isBlank() || cacheProvider.get(STATE_PREFIX + state).isEmpty()) {
            throw new BusinessException("OIDC state 无效或已过期");
        }
        cacheProvider.delete(STATE_PREFIX + state);

        KnowledgeProperties.Oidc oidc = properties.getAuth().getOidc();
        OidcEndpoints ep = loadEndpoints(oidc.getIssuerUri());

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", oidc.getRedirectUri());
        form.add("client_id", oidc.getClientId());
        form.add("client_secret", oidc.getClientSecret());

        String tokenResponse = restClient.post()
                .uri(ep.tokenEndpoint())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);

        try {
            JsonNode tokenJson = objectMapper.readTree(tokenResponse);
            String accessToken = tokenJson.path("access_token").asText(null);
            if (accessToken == null || accessToken.isBlank()) {
                throw new BusinessException("OIDC token 响应无效");
            }
            String userInfoResponse = restClient.get()
                    .uri(ep.userinfoEndpoint())
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(String.class);
            JsonNode userInfo = objectMapper.readTree(userInfoResponse);
            String sub = userInfo.path("sub").asText(null);
            if (sub == null || sub.isBlank()) {
                throw new BusinessException("OIDC userinfo 缺少 sub");
            }
            String preferredUsername = firstNonBlank(
                    userInfo.path("preferred_username").asText(null),
                    userInfo.path("email").asText(null),
                    userInfo.path("name").asText(null));
            String displayName = firstNonBlank(
                    userInfo.path("name").asText(null),
                    userInfo.path("preferred_username").asText(null),
                    sub);
            return new OidcUserInfo(sub, preferredUsername, displayName);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("OIDC token/userinfo failed: {}", e.getMessage());
            throw new BusinessException("OIDC 登录失败: " + e.getMessage());
        }
    }

    public String frontendRedirectBase() {
        String uri = properties.getAuth().getOidc().getFrontendRedirectUri();
        if (uri == null || uri.isBlank()) {
            return "/login/callback";
        }
        return uri;
    }

    private OidcEndpoints loadEndpoints(String issuerUri) {
        if (endpoints != null) {
            return endpoints;
        }
        synchronized (this) {
            if (endpoints != null) {
                return endpoints;
            }
            String discoveryUrl = issuerUri.replaceAll("/$", "") + "/.well-known/openid-configuration";
            try {
                String body = restClient.get().uri(discoveryUrl).retrieve().body(String.class);
                JsonNode json = objectMapper.readTree(body);
                endpoints = new OidcEndpoints(
                        json.path("authorization_endpoint").asText(),
                        json.path("token_endpoint").asText(),
                        json.path("userinfo_endpoint").asText());
                return endpoints;
            } catch (Exception e) {
                throw new BusinessException("无法加载 OIDC 发现文档: " + discoveryUrl);
            }
        }
    }

    private static void validateOidcConfig(KnowledgeProperties.Oidc oidc) {
        if (oidc.getIssuerUri() == null || oidc.getIssuerUri().isBlank()) {
            throw new BusinessException("OIDC issuer-uri 未配置");
        }
        if (oidc.getClientId() == null || oidc.getClientId().isBlank()) {
            throw new BusinessException("OIDC client-id 未配置");
        }
        if (oidc.getRedirectUri() == null || oidc.getRedirectUri().isBlank()) {
            throw new BusinessException("OIDC redirect-uri 未配置");
        }
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    public record OidcUserInfo(String subject, String username, String displayName) {
    }

    private record OidcEndpoints(String authorizationEndpoint, String tokenEndpoint, String userinfoEndpoint) {
    }
}
