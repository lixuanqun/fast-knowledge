package com.fast.knowledge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fast.knowledge.cache.CacheProvider;
import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.model.vo.SearchHitVO;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 搜索缓存服务 — L1（Caffeine 本地）+ L2（Redis 远程）双层 Cache-Aside。
 *
 * <p>L1 命中 &lt;1ms，适合高频热点查询；L2 Redis 命中 ~2-5ms，跨实例共享。
 * 独立缓存 Embedding 向量结果，避免同一 query 重复推理。
 * 缓存失效基于知识库版本号，文档索引变更时调用 {@link #invalidateForKb}。
 */
@Slf4j
@Service
public class SearchCacheService {

    private static final String SEARCH_PREFIX = "kb:search:";
    private static final String EMBEDDING_PREFIX = "kb:embedding:";
    private static final String VERSION_PREFIX = "kb:search:version:";
    private static final TypeReference<List<SearchHitVO>> HIT_LIST_TYPE = new TypeReference<>() {};

    private final CacheProvider cacheProvider;
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;
    private final Duration l2Ttl;
    private final Cache<String, List<SearchHitVO>> l1Cache;
    private final Cache<String, float[]> embeddingCache;
    private final boolean l1Enabled;
    private final boolean embeddingCacheEnabled;

    public SearchCacheService(CacheProvider cacheProvider,
                              ObjectMapper objectMapper,
                              MetricsService metricsService,
                              KnowledgeProperties properties) {
        this.cacheProvider = cacheProvider;
        this.objectMapper = objectMapper;
        this.metricsService = metricsService;
        int l2Minutes = properties.getSearch().getCacheTtlMinutes();
        this.l2Ttl = Duration.ofMinutes(Math.max(1, l2Minutes));

        KnowledgeProperties.L1 l1 = properties.getCache().getL1();
        this.l1Enabled = l1.isEnabled();
        this.l1Cache = l1Enabled
                ? Caffeine.newBuilder()
                    .maximumSize(l1.getMaxSize())
                    .expireAfterWrite(Duration.ofMinutes(l1.getTtlMinutes()))
                    .recordStats()
                    .build()
                : null;

        this.embeddingCacheEnabled = properties.getEmbedding().isCacheEnabled();
        this.embeddingCache = embeddingCacheEnabled
                ? Caffeine.newBuilder()
                    .maximumSize(2000)
                    .expireAfterWrite(Duration.ofMinutes(10))
                    .build()
                : null;
    }

    // ---- Search result cache ----

    public Optional<List<SearchHitVO>> get(Long kbId, String query, int topK, boolean rerank, String docType) {
        long version = getVersion(kbId);
        String key = buildKey(kbId, version, query, topK, rerank, docType);

        // L1: Caffeine local
        if (l1Enabled) {
            List<SearchHitVO> l1Result = l1Cache.getIfPresent(key);
            if (l1Result != null) {
                metricsService.recordCacheHit();
                return Optional.of(l1Result);
            }
        }

        // L2: Redis remote
        return cacheProvider.get(key)
                .flatMap(json -> {
                    try {
                        List<SearchHitVO> hits = objectMapper.readValue(json, HIT_LIST_TYPE);
                        metricsService.recordCacheHit();
                        // Backfill L1 with an unmodifiable copy to prevent cache corruption
                        if (l1Enabled) {
                            l1Cache.put(key, List.copyOf(hits));
                        }
                        return Optional.of(hits);
                    } catch (JsonProcessingException e) {
                        return Optional.empty();
                    }
                })
                .or(() -> {
                    metricsService.recordCacheMiss();
                    return Optional.empty();
                });
    }

    public void put(Long kbId, String query, int topK, boolean rerank, String docType, List<SearchHitVO> hits) {
        long version = getVersion(kbId);
        String key = buildKey(kbId, version, query, topK, rerank, docType);
        try {
            String json = objectMapper.writeValueAsString(hits);
            // Write L2 first, then backfill L1 to avoid inconsistency window
            cacheProvider.set(key, json, l2Ttl);
            if (l1Enabled) {
                l1Cache.put(key, List.copyOf(hits));
            }
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize search cache entry for kbId={}", kbId, e);
        }
    }

    // ---- Embedding cache ----

    public Optional<float[]> getEmbedding(String query) {
        if (!embeddingCacheEnabled) {
            return Optional.empty();
        }
        float[] cached = embeddingCache.getIfPresent(digestText(query));
        return Optional.ofNullable(cached != null ? cached.clone() : null);
    }

    public void putEmbedding(String query, float[] vector) {
        if (embeddingCacheEnabled) {
            embeddingCache.put(digestText(query), vector.clone());
        }
    }

    // ---- Invalidation ----

    /**
     * 递增知识库的缓存版本号，并清空 L1 + L2 旧数据。
     */
    public void invalidateForKb(Long kbId) {
        if (kbId != null) {
            cacheProvider.increment(VERSION_PREFIX + kbId, Duration.ofHours(24));
            cacheProvider.deleteByPrefix(SEARCH_PREFIX + kbId + ":");
            // L1 is version-keyed, so old entries naturally expire; but clean eagerly
            if (l1Enabled) {
                l1Cache.asMap().keySet().removeIf(k -> k.startsWith(SEARCH_PREFIX + kbId + ":"));
            }
        }
    }

    public long getVersion(Long kbId) {
        return cacheProvider.get(VERSION_PREFIX + kbId)
                .map(Long::parseLong)
                .orElse(0L);
    }

    // ---- Internal key builders ----

    private String buildKey(Long kbId, long version, String query, int topK,
                            boolean rerank, String docType) {
        return SEARCH_PREFIX + kbId + ":" + version + ":" + digestQuery(query, topK, rerank, docType);
    }

    private static String digestQuery(String query, int topK, boolean rerank, String docType) {
        return Integer.toHexString(Objects.hash(query, topK, rerank, docType));
    }

    private static String digestText(String text) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(EMBEDDING_PREFIX);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            // MD5 is guaranteed to be available in all JVMs
            throw new RuntimeException(e);
        }
    }

    // ---- Stats ----

    public CacheStats stats() {
        long hits = l1Enabled ? l1Cache.stats().hitCount() : 0;
        long misses = l1Enabled ? l1Cache.stats().missCount() : 0;
        return new CacheStats(hits, misses);
    }

    public record CacheStats(long hits, long misses) {
        public long total() { return hits + misses; }
        public double hitRate() {
            long total = total();
            return total > 0 ? (double) hits / total : 0.0;
        }
    }
}
