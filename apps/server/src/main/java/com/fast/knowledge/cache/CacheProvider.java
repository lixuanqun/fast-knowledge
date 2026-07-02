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
}
