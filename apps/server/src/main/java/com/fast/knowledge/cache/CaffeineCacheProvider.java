package com.fast.knowledge.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class CaffeineCacheProvider implements CacheProvider {

    private final Cache<String, String> valueCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .build();
    /** key -> 过期时间戳（毫秒）；无条目表示不过期 */
    private final Map<String, Long> expiryAt = new ConcurrentHashMap<>();

    @Override
    public Optional<String> get(String key) {
        if (isExpired(key)) {
            delete(key);
            return Optional.empty();
        }
        return Optional.ofNullable(valueCache.getIfPresent(key));
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        valueCache.put(key, value);
        trackExpiry(key, ttl);
    }

    @Override
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        if (valueCache.getIfPresent(key) != null && !isExpired(key)) {
            return false;
        }
        synchronized (this) {
            if (valueCache.getIfPresent(key) != null && !isExpired(key)) {
                return false;
            }
            valueCache.put(key, value);
            trackExpiry(key, ttl);
            return true;
        }
    }

    @Override
    public void delete(String key) {
        valueCache.invalidate(key);
        expiryAt.remove(key);
    }

    @Override
    public void deleteByPrefix(String prefix) {
        valueCache.asMap().keySet().removeIf(key -> key.startsWith(prefix));
        expiryAt.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private boolean isExpired(String key) {
        Long expireAt = expiryAt.get(key);
        return expireAt != null && expireAt <= System.currentTimeMillis();
    }

    private void trackExpiry(String key, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            expiryAt.remove(key);
            return;
        }
        expiryAt.put(key, System.currentTimeMillis() + ttl.toMillis());
    }
}
