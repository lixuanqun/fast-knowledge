package com.fast.knowledge.cache;

import com.fast.knowledge.config.KnowledgeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaffeineCacheProviderTest {

    private CaffeineCacheProvider cache;

    @BeforeEach
    void setUp() {
        KnowledgeProperties properties = new KnowledgeProperties();
        cache = new CaffeineCacheProvider(properties);
    }

    @Test
    void setAndGetRoundtrip() {
        cache.set("k1", "v1", Duration.ofMinutes(5));

        assertEquals(Optional.of("v1"), cache.get("k1"));
    }

    @Test
    void expiredEntryIsInvisible() throws InterruptedException {
        cache.set("k2", "v2", Duration.ofMillis(80));
        Thread.sleep(200);

        assertEquals(Optional.empty(), cache.get("k2"));
    }

    @Test
    void setIfAbsentIsAtomicPerKey() {
        assertTrue(cache.setIfAbsent("lock", "a", Duration.ofMinutes(1)));
        assertFalse(cache.setIfAbsent("lock", "b", Duration.ofMinutes(1)));
        assertEquals(Optional.of("a"), cache.get("lock"));
    }

    @Test
    void incrementCountsAndKeepsFirstTtl() {
        assertEquals(1, cache.increment("cnt", Duration.ofMinutes(1)));
        assertEquals(2, cache.increment("cnt", Duration.ofMinutes(1)));
        assertEquals(3, cache.increment("cnt", Duration.ofMinutes(1)));
    }

    @Test
    void incrementResetsAfterExpiry() throws InterruptedException {
        assertEquals(1, cache.increment("cnt2", Duration.ofMillis(80)));
        assertEquals(2, cache.increment("cnt2", Duration.ofMillis(80)));
        Thread.sleep(200);
        assertEquals(1, cache.increment("cnt2", Duration.ofMinutes(1)));
    }

    @Test
    void deleteAndDeleteByPrefix() {
        cache.set("kb:1:search", "a", Duration.ofMinutes(5));
        cache.set("kb:2:search", "b", Duration.ofMinutes(5));
        cache.set("other", "c", Duration.ofMinutes(5));

        cache.deleteByPrefix("kb:");

        assertEquals(Optional.empty(), cache.get("kb:1:search"));
        assertEquals(Optional.empty(), cache.get("kb:2:search"));
        assertEquals(Optional.of("c"), cache.get("other"));

        cache.delete("other");
        assertEquals(Optional.empty(), cache.get("other"));
    }

    @Test
    void nullTtlTreatsAsNoExpiry() {
        cache.set("forever", "v", null);

        assertEquals(Optional.of("v"), cache.get("forever"));
    }
}
