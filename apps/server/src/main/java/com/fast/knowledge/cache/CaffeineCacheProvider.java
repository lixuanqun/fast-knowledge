package com.fast.knowledge.cache;

import com.fast.knowledge.config.KnowledgeProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.Scheduler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Caffeine 单机缓存 — knowledge.cache.provider=caffeine 时的 Redis 替代实现。
 *
 * <p>单 JVM 内语义与 Redis 对齐（TTL / setIfAbsent 原子性 / 计数），跨实例不共享：
 * 登录会话、Token 黑名单、检索缓存仅对当前实例生效，多实例部署必须回到 redis 模式。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "knowledge.cache.provider", havingValue = "caffeine")
public class CaffeineCacheProvider implements CacheProvider {

    private record Entry(String value, long expireAtNanos) {
        boolean expired(long now) {
            return expireAtNanos <= now;
        }
    }

    private final Cache<String, Entry> cache;

    public CaffeineCacheProvider(KnowledgeProperties properties) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(properties.getCache().getLocalMaxSize())
                .scheduler(Scheduler.systemScheduler())
                .expireAfter(new Expiry<String, Entry>() {
                    @Override
                    public long expireAfterCreate(String key, Entry entry, long now) {
                        return Math.max(1, entry.expireAtNanos() - now);
                    }

                    @Override
                    public long expireAfterUpdate(String key, Entry entry, long now, long currentTtl) {
                        return Math.max(1, entry.expireAtNanos() - now);
                    }

                    @Override
                    public long expireAfterRead(String key, Entry entry, long now, long currentTtl) {
                        return currentTtl;
                    }
                })
                .build();
        log.info("缓存模式 none：使用 Caffeine 单机缓存（上限 {} 条），Redis 未接入",
                properties.getCache().getLocalMaxSize());
    }

    @Override
    public Optional<String> get(String key) {
        Entry entry = cache.getIfPresent(key);
        if (entry == null) {
            return Optional.empty();
        }
        long now = System.nanoTime();
        if (entry.expired(now)) {
            cache.invalidate(key);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        cache.put(key, new Entry(value, deadline(ttl)));
    }

    @Override
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        boolean[] added = {false};
        cache.asMap().compute(key, (k, existing) -> {
            if (existing != null && !existing.expired(System.nanoTime())) {
                return existing;
            }
            added[0] = true;
            return new Entry(value, deadline(ttl));
        });
        return added[0];
    }

    @Override
    public void delete(String key) {
        cache.invalidate(key);
    }

    @Override
    public void deleteByPrefix(String prefix) {
        cache.asMap().keySet().removeIf(key -> key.startsWith(prefix));
    }

    @Override
    public int increment(String key, Duration ttl) {
        int[] result = {1};
        cache.asMap().compute(key, (k, existing) -> {
            long now = System.nanoTime();
            if (existing != null && !existing.expired(now)) {
                try {
                    result[0] = Integer.parseInt(existing.value()) + 1;
                } catch (NumberFormatException e) {
                    result[0] = 1;
                }
                return new Entry(String.valueOf(result[0]), existing.expireAtNanos());
            }
            result[0] = 1;
            return new Entry("1", deadline(ttl));
        });
        return result[0];
    }

    private long deadline(Duration ttl) {
        long nanos = ttl == null ? Long.MAX_VALUE : ttl.toNanos();
        if (nanos <= 0 || nanos == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        long now = System.nanoTime();
        return now + nanos < 0 ? Long.MAX_VALUE : now + nanos;
    }
}
