package com.fast.knowledge.cache;

import java.time.Duration;
import java.util.Optional;

public interface CacheProvider {

    Optional<String> get(String key);

    void set(String key, String value, Duration ttl);

    boolean setIfAbsent(String key, String value, Duration ttl);

    void delete(String key);
}
