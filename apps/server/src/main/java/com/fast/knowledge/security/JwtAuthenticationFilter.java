package com.fast.knowledge.security;

import com.fast.knowledge.cache.CacheProvider;
import com.fast.knowledge.mapper.UserMapper;
import com.fast.knowledge.model.entity.KbUser;
import com.fast.knowledge.service.ApiKeyService;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;
    private final CacheProvider cacheProvider;
    private final ApiKeyService apiKeyService;
    private final UserMapper userMapper;

    public JwtAuthenticationFilter(JwtUtil jwtUtil,
                                   TokenBlacklistService tokenBlacklistService,
                                   CacheProvider cacheProvider,
                                   ApiKeyService apiKeyService,
                                   UserMapper userMapper) {
        this.jwtUtil = jwtUtil;
        this.tokenBlacklistService = tokenBlacklistService;
        this.cacheProvider = cacheProvider;
        this.apiKeyService = apiKeyService;
        this.userMapper = userMapper;
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            if (authenticateBearer(request)) {
                // JWT 已设置上下文
            } else {
                authenticateApiKey(request);
            }
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
            if (!request.isAsyncStarted()) {
                SecurityContextHolder.clearContext();
            }
        }
    }

    private boolean authenticateBearer(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return false;
        }
        String token = auth.substring(7);
        if (tokenBlacklistService.isBlacklisted(token)) {
            return false;
        }
        try {
            DecodedJWT jwt = jwtUtil.verify(token);
            AuthenticatedUser user = new AuthenticatedUser(
                    jwt.getClaim("userId").asLong(),
                    jwt.getClaim("username").asString(),
                    jwt.getClaim("role").asString()
            );
            setSecurityContext(user, token);
            cacheProvider.set("kb:session:" + user.getUserId(), token, Duration.ofHours(24));
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void authenticateApiKey(HttpServletRequest request) {
        String rawKey = resolveApiKey(request);
        if (rawKey == null) {
            return;
        }
        apiKeyService.authenticate(rawKey).ifPresent(apiKey -> {
            KbUser user = userMapper.selectById(apiKey.getUserId());
            if (user != null && user.getStatus() == 1) {
                setSecurityContext(
                        new AuthenticatedUser(user.getId(), user.getUsername(), user.getRole()),
                        "api-key:" + apiKey.getId(),
                        apiKey.getKbId());
            }
        });
    }

    private void setSecurityContext(AuthenticatedUser user, String credential) {
        setSecurityContext(user, credential, null);
    }

    private void setSecurityContext(AuthenticatedUser user, String credential, Long scopedKbId) {
        UserContext ctx = new UserContext();
        ctx.setUserId(user.getUserId());
        ctx.setUsername(user.getUsername());
        ctx.setRole(user.getRole());
        ctx.setScopedKbId(scopedKbId);
        UserContext.set(ctx);
        String role = user.getRole() != null ? user.getRole() : "USER";
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                user,
                credential,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String resolveApiKey(HttpServletRequest request) {
        String header = request.getHeader("X-API-Key");
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("ApiKey ")) {
            return auth.substring(7).trim();
        }
        return null;
    }
}
