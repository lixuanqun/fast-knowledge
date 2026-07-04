package com.fast.knowledge.cache;

import java.time.Duration;
import java.util.Optional;

public interface CacheProvider {

    Optional<String> get(String key);

    void set(String key, String value, Duration ttl);

    boolean setIfAbsent(String key, String value, Duration ttl);

    void delete(String key);

    /** 删除所有以 prefix 开头的缓存键（用于按知识库失效检索缓存等场景）。 */
    void deleteByPrefix(String prefix);

    /**
     * 原子自增并返回新值。Key 首次访问时从 0 开始计数，TTL 仅在首次 set 时生效。
     *
     * @param key 缓存键
     * @param ttl 过期时间
     * @return 自增后的值（首次调用返回 1）
     */
    int increment(String key, Duration ttl);
}
