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
    void invalidateForKb_removesOnlyMatchingKbKeys() {
        searchCacheService.put(1L, "hello", 8, false, null, List.of(new SearchHitVO()));
        searchCacheService.put(2L, "hello", 8, false, null, List.of(new SearchHitVO()));

        String kb1Key = searchCacheService.buildKey(1L, "hello", 8, false, null);
        String kb2Key = searchCacheService.buildKey(2L, "hello", 8, false, null);
        assertTrue(cacheProvider.get(kb1Key).isPresent());
        assertTrue(cacheProvider.get(kb2Key).isPresent());

        searchCacheService.invalidateForKb(1L);

        assertTrue(cacheProvider.get(kb1Key).isEmpty());
        assertTrue(cacheProvider.get(kb2Key).isPresent());
    }

    @Test
    void buildKey_isStableForSameInput() {
        String key1 = searchCacheService.buildKey(3L, "query", 5, false, null);
        String key2 = searchCacheService.buildKey(3L, "query", 5, false, null);
        assertEquals(key1, key2);
        assertTrue(key1.startsWith("kb:search:3:"));
    }

    @Test
    void buildKey_differsWhenRerankFlagChanges() {
        String withoutRerank = searchCacheService.buildKey(3L, "query", 5, false, null);
        String withRerank = searchCacheService.buildKey(3L, "query", 5, true, null);
        assertNotEquals(withoutRerank, withRerank);
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

    private static final class InMemoryCacheProvider implements CacheProvider {
        private final Map<String, String> store = new ConcurrentHashMap<>();
        private final Map<String, Long> expiry = new ConcurrentHashMap<>();

        @Override
        public Optional<String> get(String key) {
            Long exp = expiry.get(key);
            if (exp != null && System.currentTimeMillis() > exp) {
                store.remove(key);
                expiry.remove(key);
                return Optional.empty();
            }
            return Optional.ofNullable(store.get(key));
        }

        @Override
        public void set(String key, String value, Duration ttl) {
            store.put(key, value);
            expiry.put(key, System.currentTimeMillis() + ttl.toMillis());
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
            expiry.remove(key);
        }

        @Override
        public void deleteByPrefix(String prefix) {
            store.keySet().removeIf(k -> k.startsWith(prefix));
            expiry.keySet().removeIf(k -> k.startsWith(prefix));
        }
    }
}
