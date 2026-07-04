package com.fast.knowledge.service;

import com.fast.knowledge.cache.CacheProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.model.vo.SearchHitVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchCacheServiceTest {

    private CacheProvider cacheProvider;
    private SearchCacheService searchCacheService;

    @BeforeEach
    void setUp() {
        cacheProvider = new InMemoryCacheProvider();
        KnowledgeProperties properties = new KnowledgeProperties();
        searchCacheService = new SearchCacheService(cacheProvider, new ObjectMapper(), properties);
    }

    @Test
    void invalidateForKb_incrementsVersionAndClearsOldKeys() {
        long v1 = searchCacheService.getVersion(1L);
        searchCacheService.put(1L, "hello", 8, false, null, List.of(new SearchHitVO()));
        searchCacheService.put(2L, "hello", 8, false, null, List.of(new SearchHitVO()));

        assertTrue(searchCacheService.get(1L, "hello", 8, false, null).isPresent());
        assertTrue(searchCacheService.get(2L, "hello", 8, false, null).isPresent());

        searchCacheService.invalidateForKb(1L);

        long v2 = searchCacheService.getVersion(1L);
        assertNotEquals(v1, v2, "invalidate should increment version");
        // Old version cache should be unreachable
        assertTrue(searchCacheService.get(1L, "hello", 8, false, null).isEmpty(),
                "stale cache should be cleanable");
        assertTrue(searchCacheService.get(2L, "hello", 8, false, null).isPresent());
    }

    @Test
    void get_returnsCachedHits() {
        SearchHitVO hit = new SearchHitVO();
        hit.setChunkId(1L);
        hit.setContent("test");
        searchCacheService.put(1L, "q", 8, false, null, List.of(hit));

        List<SearchHitVO> cached = searchCacheService.get(1L, "q", 8, false, null).orElseThrow();
        assertEquals(1, cached.size());
        assertEquals("test", cached.getFirst().getContent());
    }

    @Test
    void cacheStats_tracksHitsAndMisses() {
        searchCacheService.get(99L, "never-cached", 8, false, null);
        SearchCacheService.CacheStats stats = searchCacheService.stats();
        assertTrue(stats.misses() > 0, "miss should be tracked");
    }

    @SuppressWarnings("unchecked")
    private static final class InMemoryCacheProvider implements CacheProvider {
        private final Map<String, Object> store = new ConcurrentHashMap<>();

        @Override
        public Optional<String> get(String key) {
            Object val = store.get(key);
            if (val instanceof ExpiringValue ev) {
                if (System.currentTimeMillis() > ev.expiresAt) {
                    store.remove(key);
                    return Optional.empty();
                }
                return Optional.ofNullable(ev.value);
            }
            return Optional.ofNullable((String) val);
        }

        @Override
        public void set(String key, String value, Duration ttl) {
            store.put(key, new ExpiringValue(value, System.currentTimeMillis() + ttl.toMillis()));
        }

        @Override
        public boolean setIfAbsent(String key, String value, Duration ttl) {
            if (store.containsKey(key)) {
                return false;
            }
            set(key, value, ttl);
            return true;
        }

        @Override
        public void delete(String key) {
            store.remove(key);
        }

        @Override
        public void deleteByPrefix(String prefix) {
            store.keySet().removeIf(k -> k.startsWith(prefix));
        }

        @Override
        public int increment(String key, Duration ttl) {
            Object existing = store.get(key);
            long current = 0;
            if (existing instanceof String s) {
                try {
                    current = Long.parseLong(s);
                } catch (NumberFormatException ignored) {
                }
            }
            long next = current + 1;
            store.put(key, String.valueOf(next));
            return (int) next;
        }

        private record ExpiringValue(String value, long expiresAt) {}
    }
}
