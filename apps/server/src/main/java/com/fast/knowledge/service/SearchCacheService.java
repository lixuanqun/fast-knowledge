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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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
    private final boolean semanticEnabled;
    private final double semanticThreshold;
    private final int semanticMaxPerKb;
    /** WP4 语义缓存索引：kbId -> 条目（查询向量 + 精确缓存 key），命中后走既有 L1/L2 取结果 */
    private final java.util.concurrent.ConcurrentHashMap<Long, List<SemanticEntry>> semanticIndex = new ConcurrentHashMap<>();

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

        KnowledgeProperties.SemanticCache semantic = properties.getSearch().getSemanticCache();
        this.semanticEnabled = semantic.isEnabled();
        this.semanticThreshold = semantic.getThreshold();
        this.semanticMaxPerKb = Math.max(1, semantic.getMaxEntriesPerKb());
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

    // ---- WP4 语义缓存 ----

    /**
     * 语义缓存查找：精确 key 未命中时，按查询向量近邻（余弦 ≥ 阈值）匹配缓存条目。
     * 仅匹配相同参数（topK/rerank/docType/版本）的条目；命中后从既有 L1/L2 取结果。
     */
    public Optional<List<SearchHitVO>> getSemantic(Long kbId, float[] queryVec, int topK,
                                                   boolean rerank, String docType) {
        if (!semanticEnabled || queryVec == null) {
            return Optional.empty();
        }
        long version = getVersion(kbId);
        long now = System.currentTimeMillis();
        String bestKey = null;
        double bestScore = 0;
        List<SemanticEntry> entries = semanticIndex.get(kbId);
        if (entries == null) {
            return Optional.empty();
        }
        synchronized (entries) {
            Iterator<SemanticEntry> it = entries.iterator();
            while (it.hasNext()) {
                SemanticEntry e = it.next();
                if (e.expireAt < now) {
                    it.remove();
                    continue;
                }
                if (e.topK != topK || e.rerank != rerank || !Objects.equals(e.docType, docType) || e.version != version) {
                    continue;
                }
                double sim = cosine(queryVec, e.vec);
                if (sim >= semanticThreshold && sim > bestScore) {
                    bestScore = sim;
                    bestKey = e.key;
                }
            }
        }
        if (bestKey == null) {
            return Optional.empty();
        }
        Optional<List<SearchHitVO>> hit = fetchByKey(bestKey);
        if (hit.isPresent()) {
            log.info("语义缓存命中 kbId={} sim={} key={}", kbId, String.format("%.3f", bestScore), bestKey);
        }
        return hit;
    }

    /** 结果写入缓存并登记语义索引（供近邻命中） */
    public void putWithVector(Long kbId, String query, float[] queryVec, int topK,
                              boolean rerank, String docType, List<SearchHitVO> hits) {
        long version = getVersion(kbId);
        String key = buildKey(kbId, version, query, topK, rerank, docType);
        try {
            String json = objectMapper.writeValueAsString(hits);
            cacheProvider.set(key, json, l2Ttl);
            if (l1Enabled) {
                l1Cache.put(key, List.copyOf(hits));
            }
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize search cache entry for kbId={}", kbId, e);
            return;
        }
        if (semanticEnabled && queryVec != null) {
            long expireAt = System.currentTimeMillis() + l2Ttl.toMillis();
            List<SemanticEntry> entries = semanticIndex.computeIfAbsent(kbId, k -> new ArrayList<>());
            synchronized (entries) {
                entries.removeIf(e -> e.key.equals(key));
                entries.add(new SemanticEntry(key, queryVec.clone(), topK, rerank, docType, version, expireAt));
                while (entries.size() > semanticMaxPerKb) {
                    entries.remove(0);
                }
            }
        }
    }

    private Optional<List<SearchHitVO>> fetchByKey(String key) {
        if (l1Enabled) {
            List<SearchHitVO> l1 = l1Cache.getIfPresent(key);
            if (l1 != null) {
                metricsService.recordCacheHit();
                return Optional.of(l1);
            }
        }
        return cacheProvider.get(key)
                .flatMap(json -> {
                    try {
                        List<SearchHitVO> hits = objectMapper.readValue(json, HIT_LIST_TYPE);
                        if (l1Enabled) {
                            l1Cache.put(key, List.copyOf(hits));
                        }
                        metricsService.recordCacheHit();
                        return Optional.of(hits);
                    } catch (JsonProcessingException e) {
                        return Optional.empty();
                    }
                });
    }

    private static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0;
        }
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        return na == 0 || nb == 0 ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private record SemanticEntry(String key, float[] vec, int topK, boolean rerank,
                                 String docType, long version, long expireAt) {
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
            semanticIndex.remove(kbId);
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
