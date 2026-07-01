package com.fast.knowledge.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig {

    @Bean
    @ConditionalOnProperty(name = "knowledge.cache.provider", havingValue = "caffeine", matchIfMissing = true)
    public CacheProvider caffeineCacheProvider() {
        return new CaffeineCacheProvider();
    }
}
