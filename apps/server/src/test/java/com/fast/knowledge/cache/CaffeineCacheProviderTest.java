package com.fast.knowledge.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaffeineCacheProviderTest {

    private CaffeineCacheProvider cache;

    @BeforeEach
    void setUp() {
        cache = new CaffeineCacheProvider();
    }

    @Test
    void set_respectsTtl() throws InterruptedException {
        cache.set("key", "value", Duration.ofMillis(50));
        assertEquals("value", cache.get("key").orElseThrow());

        Thread.sleep(60);
        assertTrue(cache.get("key").isEmpty());
    }

    @Test
    void deleteByPrefix_removesMatchingKeys() {
        cache.set("kb:search:1:a", "a", Duration.ofMinutes(5));
        cache.set("kb:search:1:b", "b", Duration.ofMinutes(5));
        cache.set("kb:search:2:a", "c", Duration.ofMinutes(5));

        cache.deleteByPrefix("kb:search:1:");

        assertTrue(cache.get("kb:search:1:a").isEmpty());
        assertTrue(cache.get("kb:search:1:b").isEmpty());
        assertEquals("c", cache.get("kb:search:2:a").orElseThrow());
    }
}
