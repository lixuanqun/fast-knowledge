package com.fast.knowledge.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "knowledge.cache.provider", havingValue = "redis")
public class RedisCacheProvider implements CacheProvider {

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
}
