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
    private final Map<String, Long> lockExpiry = new ConcurrentHashMap<>();

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(valueCache.getIfPresent(key));
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        valueCache.put(key, value);
    }

    @Override
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        long expireAt = System.currentTimeMillis() + ttl.toMillis();
        Long existing = lockExpiry.get(key);
        if (existing != null && existing > System.currentTimeMillis()) {
            return false;
        }
        synchronized (lockExpiry) {
            existing = lockExpiry.get(key);
            if (existing != null && existing > System.currentTimeMillis()) {
                return false;
            }
            lockExpiry.put(key, expireAt);
            valueCache.put(key, value);
            return true;
        }
    }

    @Override
    public void delete(String key) {
        valueCache.invalidate(key);
        lockExpiry.remove(key);
    }
}
