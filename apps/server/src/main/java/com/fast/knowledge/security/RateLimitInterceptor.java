package com.fast.knowledge.security;

import com.fast.knowledge.cache.CacheProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/**
 * 基于 Redis 固定窗口的限流拦截器。识别标注了 {@link RateLimit} 的 Controller 方法，
 * 按 userId + 方法签名作为维度计数，超阈值返回 429。
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final String KEY_PREFIX = "kb:ratelimit:";

    private final CacheProvider cacheProvider;

    public RateLimitInterceptor(CacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }
        RateLimit annotation = hm.getMethodAnnotation(RateLimit.class);
        if (annotation == null) {
            return true;
        }

        String identity = resolveIdentity(request);
        if (identity == null) {
            return true;
        }

        String key = KEY_PREFIX + identity + ":" + hm.getMethod().getDeclaringClass().getSimpleName()
                + "." + hm.getMethod().getName();
        int current = cacheProvider.increment(key, Duration.ofSeconds(annotation.windowSeconds()));
        if (current > annotation.maxRequests()) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            try {
                response.getWriter().write(
                        "{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\",\"data\":null}");
                response.flushBuffer();
            } catch (java.io.IOException ignored) {
            }
            return false;
        }
        return true;
    }

    /** Resolve rate-limit identity: userId for authenticated users, IP for unauthenticated. */
    private String resolveIdentity(HttpServletRequest request) {
        UserContext ctx = UserContext.get();
        if (ctx != null && ctx.getUserId() != null) {
            return "user:" + ctx.getUserId();
        }
        // Fallback to IP for unauthenticated endpoints (e.g., login)
        String ip = getClientIp(request);
        return ip != null && !ip.isBlank() ? "ip:" + ip : null;
    }

    private static String getClientIp(HttpServletRequest request) {
        // X-Real-IP is set by nginx/reverse-proxy from the actual TCP connection,
        // not forwarded from client headers, so it cannot be spoofed.
        String ip = request.getHeader("X-Real-IP");
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
