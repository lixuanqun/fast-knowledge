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

@Service
public class SearchCacheService {

    private static final String SEARCH_PREFIX = "kb:search:";
    private static final TypeReference<List<SearchHitVO>> HIT_LIST_TYPE = new TypeReference<>() {};

    private final CacheProvider cacheProvider;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;

    public SearchCacheService(CacheProvider cacheProvider,
                              ObjectMapper objectMapper,
                              KnowledgeProperties properties) {
        this.cacheProvider = cacheProvider;
        this.objectMapper = objectMapper;
        int minutes = properties.getSearch().getCacheTtlMinutes();
        this.cacheTtl = Duration.ofMinutes(Math.max(1, minutes));
    }

    public Optional<List<SearchHitVO>> get(Long kbId, String query, int topK, boolean rerank, String docType) {
        return cacheProvider.get(buildKey(kbId, query, topK, rerank, docType))
                .flatMap(json -> {
                    try {
                        return Optional.of(objectMapper.readValue(json, HIT_LIST_TYPE));
                    } catch (JsonProcessingException e) {
                        cacheProvider.delete(buildKey(kbId, query, topK, rerank, docType));
                        return Optional.empty();
                    }
                });
    }

    public void put(Long kbId, String query, int topK, boolean rerank, String docType, List<SearchHitVO> hits) {
        try {
            cacheProvider.set(buildKey(kbId, query, topK, rerank, docType),
                    objectMapper.writeValueAsString(hits), cacheTtl);
        } catch (JsonProcessingException ignored) {
        }
    }

    public void invalidateForKb(Long kbId) {
        if (kbId != null) {
            cacheProvider.deleteByPrefix(SEARCH_PREFIX + kbId + ":");
        }
    }

    String buildKey(Long kbId, String query, int topK, boolean rerank, String docType) {
        return SEARCH_PREFIX + kbId + ":" + digestQuery(query, topK, rerank, docType);
    }

    private static String digestQuery(String query, int topK, boolean rerank, String docType) {
        return Integer.toHexString(Objects.hash(query, topK, rerank, docType));
    }
}
