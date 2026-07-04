package com.fast.knowledge.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "knowledge.cache.provider", havingValue = "redis", matchIfMissing = true)
public class RedisCacheProvider implements CacheProvider {

    private static final int SCAN_BATCH_SIZE = 256;

    private final StringRedisTemplate redisTemplate;

    public RedisCacheProvider(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, value, ttl.toMillis(), TimeUnit.MILLISECONDS);
        return Boolean.TRUE.equals(ok);
    }

    @Override
    public void delete(String key) {
        redisTemplate.delete(key);
    }

    @Override
    public void deleteByPrefix(String prefix) {
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            ScanOptions options = ScanOptions.scanOptions()
                    .match(prefix + "*")
                    .count(SCAN_BATCH_SIZE)
                    .build();
            List<byte[]> batch = new ArrayList<>(SCAN_BATCH_SIZE);
            try (Cursor<byte[]> cursor = connection.scan(options)) {
                while (cursor.hasNext()) {
                    batch.add(cursor.next());
                    if (batch.size() >= SCAN_BATCH_SIZE) {
                        connection.del(batch.toArray(new byte[0][]));
                        batch.clear();
                    }
                }
                if (!batch.isEmpty()) {
                    connection.del(batch.toArray(new byte[0][]));
                }
            }
            return null;
        });
    }

    @Override
    public int increment(String key, Duration ttl) {
        Long value = redisTemplate.opsForValue().increment(key);
        if (value != null && value == 1) {
            redisTemplate.expire(key, ttl.toMillis(), TimeUnit.MILLISECONDS);
        }
        return value != null ? value.intValue() : 0;
    }
}
