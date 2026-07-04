package com.fast.knowledge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fast.knowledge.cache.CacheProvider;
import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.model.vo.SearchHitVO;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 搜索缓存服务 — Cache-Aside 模式，基于版本号的跨节点失效机制。
 *
 * <p>每次 invalidateForKb 递增该知识库的缓存版本号，后续新搜索使用新版本号键，
 * 旧版本数据自然过期（TTL）。相比 deleteByPrefix 更原子，避免大键族删除。
 */
@Service
public class SearchCacheService {

    private static final String SEARCH_PREFIX = "kb:search:";
    private static final String VERSION_PREFIX = "kb:search:version:";
    private static final TypeReference<List<SearchHitVO>> HIT_LIST_TYPE = new TypeReference<>() {};

    private final CacheProvider cacheProvider;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;

    /** 缓存统计 — 仅用于监控，进程内非精确计数 */
    private final AtomicLong hitCount = new AtomicLong();
    private final AtomicLong missCount = new AtomicLong();

    public SearchCacheService(CacheProvider cacheProvider,
                              ObjectMapper objectMapper,
                              KnowledgeProperties properties) {
        this.cacheProvider = cacheProvider;
        this.objectMapper = objectMapper;
        int minutes = properties.getSearch().getCacheTtlMinutes();
        this.cacheTtl = Duration.ofMinutes(Math.max(1, minutes));
    }

    public Optional<List<SearchHitVO>> get(Long kbId, String query, int topK, boolean rerank, String docType) {
        long version = getVersion(kbId);
        return cacheProvider.get(buildKey(kbId, version, query, topK, rerank, docType))
                .flatMap(json -> {
                    try {
                        List<SearchHitVO> hits = objectMapper.readValue(json, HIT_LIST_TYPE);
                        hitCount.incrementAndGet();
                        return Optional.of(hits);
                    } catch (JsonProcessingException e) {
                        missCount.incrementAndGet();
                        return Optional.empty();
                    }
                })
                .or(() -> {
                    missCount.incrementAndGet();
                    return Optional.empty();
                });
    }

    public void put(Long kbId, String query, int topK, boolean rerank, String docType, List<SearchHitVO> hits) {
        long version = getVersion(kbId);
        try {
            cacheProvider.set(buildKey(kbId, version, query, topK, rerank, docType),
                    objectMapper.writeValueAsString(hits), cacheTtl);
        } catch (JsonProcessingException ignored) {
        }
    }

    /**
     * 递增知识库的缓存版本号。旧版本键自然过期，无需显式删除。
     * <p>版本号每 24 小时重置，防止无限增长。
     */
    public void invalidateForKb(Long kbId) {
        if (kbId != null) {
            cacheProvider.increment(VERSION_PREFIX + kbId, Duration.ofHours(24));
            // 同时清理旧版本数据，避免短期重复索引造成的残留
            cacheProvider.deleteByPrefix(SEARCH_PREFIX + kbId + ":");
        }
    }

    /** 获取当前 kbId 的缓存版本号 */
    public long getVersion(Long kbId) {
        return cacheProvider.get(VERSION_PREFIX + kbId)
                .map(Long::parseLong)
                .orElse(0L);
    }

    /** 返回缓存统计数据 */
    public CacheStats stats() {
        return new CacheStats(hitCount.get(), missCount.get());
    }

    private String buildKey(Long kbId, long version, String query, int topK,
                            boolean rerank, String docType) {
        return SEARCH_PREFIX + kbId + ":" + version + ":" + digestQuery(query, topK, rerank, docType);
    }

    private static String digestQuery(String query, int topK, boolean rerank, String docType) {
        return Integer.toHexString(Objects.hash(query, topK, rerank, docType));
    }

    /** 缓存统计值对象 */
    public record CacheStats(long hits, long misses) {
        public long total() { return hits + misses; }
        public double hitRate() {
            long total = total();
            return total > 0 ? (double) hits / total : 0.0;
        }
    }
}
