package com.fast.knowledge.security;

import com.fast.knowledge.cache.CacheProvider;
import com.fast.knowledge.mapper.SystemConfigMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

@Service
public class TokenBlacklistService {

    private static final String CACHE_PREFIX = "kb:token:blacklist:";
    private static final String CONFIG_PREFIX = "token.blacklist.";

    private final CacheProvider cacheProvider;
    private final SystemConfigMapper systemConfigMapper;
    private final JwtUtil jwtUtil;

    public TokenBlacklistService(CacheProvider cacheProvider,
                                 SystemConfigMapper systemConfigMapper,
                                 JwtUtil jwtUtil) {
        this.cacheProvider = cacheProvider;
        this.systemConfigMapper = systemConfigMapper;
        this.jwtUtil = jwtUtil;
    }

    public void blacklist(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        long expiresAt = resolveExpiresAt(token);
        Duration ttl = Duration.ofMillis(Math.max(expiresAt - System.currentTimeMillis(), 60_000));
        cacheProvider.set(CACHE_PREFIX + token, "1", ttl);
        systemConfigMapper.upsert(CONFIG_PREFIX + hashToken(token), String.valueOf(expiresAt));
    }

    public boolean isBlacklisted(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        if (cacheProvider.get(CACHE_PREFIX + token).isPresent()) {
            return true;
        }
        String stored = systemConfigMapper.getValue(CONFIG_PREFIX + hashToken(token));
        if (stored == null) {
            return false;
        }
        try {
            long expiresAt = Long.parseLong(stored);
            if (expiresAt > System.currentTimeMillis()) {
                cacheProvider.set(CACHE_PREFIX + token, "1",
                        Duration.ofMillis(expiresAt - System.currentTimeMillis()));
                return true;
            }
            return false;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private long resolveExpiresAt(String token) {
        try {
            return jwtUtil.verify(token).getExpiresAt().getTime();
        } catch (Exception e) {
            return System.currentTimeMillis() + Duration.ofHours(24).toMillis();
        }
    }

    private static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
