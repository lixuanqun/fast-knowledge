package com.fast.knowledge.service;

import com.fast.knowledge.cache.CaffeineCacheProvider;
import com.fast.knowledge.cache.CacheProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.model.vo.SearchHitVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchCacheServiceTest {

    private CacheProvider cacheProvider;
    private SearchCacheService searchCacheService;

    @BeforeEach
    void setUp() {
        cacheProvider = new CaffeineCacheProvider();
        KnowledgeProperties properties = new KnowledgeProperties();
        searchCacheService = new SearchCacheService(cacheProvider, new ObjectMapper(), properties);
    }

    @Test
    void invalidateForKb_removesOnlyMatchingKbKeys() {
        searchCacheService.put(1L, "hello", 8, 0.6, List.of(new SearchHitVO()));
        searchCacheService.put(2L, "hello", 8, 0.6, List.of(new SearchHitVO()));

        String kb1Key = searchCacheService.buildKey(1L, "hello", 8, 0.6);
        String kb2Key = searchCacheService.buildKey(2L, "hello", 8, 0.6);
        assertTrue(cacheProvider.get(kb1Key).isPresent());
        assertTrue(cacheProvider.get(kb2Key).isPresent());

        searchCacheService.invalidateForKb(1L);

        assertTrue(cacheProvider.get(kb1Key).isEmpty());
        assertTrue(cacheProvider.get(kb2Key).isPresent());
    }

    @Test
    void buildKey_isStableForSameInput() {
        String key1 = searchCacheService.buildKey(3L, "query", 5, 0.5);
        String key2 = searchCacheService.buildKey(3L, "query", 5, 0.5);
        assertEquals(key1, key2);
        assertTrue(key1.startsWith("kb:search:3:"));
    }

    @Test
    void get_returnsCachedHits() {
        SearchHitVO hit = new SearchHitVO();
        hit.setChunkId(1L);
        hit.setContent("test");
        searchCacheService.put(1L, "q", 8, 0.6, List.of(hit));

        List<SearchHitVO> cached = searchCacheService.get(1L, "q", 8, 0.6).orElseThrow();
        assertEquals(1, cached.size());
        assertEquals("test", cached.getFirst().getContent());
    }
}
