package com.fast.knowledge.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fast.knowledge.cache.CacheProvider;
import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.embedding.EmbeddingProvider;
import com.fast.knowledge.model.dto.SearchRequest;
import com.fast.knowledge.model.entity.KnowledgeBase;
import com.fast.knowledge.model.vo.SearchHitVO;
import com.fast.knowledge.vector.SearchHit;
import com.fast.knowledge.vector.VectorStore;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SearchService {

    private final KnowledgeBaseService knowledgeBaseService;
    private final EmbeddingProvider embeddingProvider;
    private final VectorStore vectorStore;
    private final CacheProvider cacheProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SearchService(KnowledgeBaseService knowledgeBaseService,
                         EmbeddingProvider embeddingProvider,
                         VectorStore vectorStore,
                         CacheProvider cacheProvider) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.embeddingProvider = embeddingProvider;
        this.vectorStore = vectorStore;
        this.cacheProvider = cacheProvider;
    }

    public List<SearchHitVO> search(SearchRequest request) throws Exception {
        if (request.getKbId() == null) {
            throw new BusinessException("请指定知识库");
        }
        if (request.getQuery() == null || request.getQuery().isBlank()) {
            throw new BusinessException("检索内容不能为空");
        }
        KnowledgeBase kb = knowledgeBaseService.getById(request.getKbId());
        int topK = request.getTopK() != null ? request.getTopK() : kb.getSearchTopK();
        double alpha = request.getAlpha() != null ? request.getAlpha() : kb.getSearchAlpha();

        String cacheKey = "kb:search:" + digestKey(kb.getId(), request.getQuery(), topK, alpha);
        var cached = cacheProvider.get(cacheKey);
        if (cached.isPresent()) {
            return objectMapper.readValue(cached.get(), new TypeReference<List<SearchHitVO>>() {});
        }

        float[] vector = embeddingProvider.embed(request.getQuery());
        List<SearchHit> hits = vectorStore.hybridSearch(kb.getId(), request.getQuery(), vector, topK, alpha);

        List<SearchHitVO> result = hits.stream().map(h -> {
            SearchHitVO vo = new SearchHitVO();
            vo.setChunkId(h.getChunkId());
            vo.setDocumentId(h.getDocumentId());
            vo.setDocumentTitle(h.getTitle());
            vo.setContent(h.getContent());
            vo.setScore(h.getScore());
            return vo;
        }).collect(Collectors.toList());

        cacheProvider.set(cacheKey, objectMapper.writeValueAsString(result), Duration.ofMinutes(5));
        return result;
    }

    private String digestKey(Long kbId, String query, int topK, double alpha) {
        String raw = kbId + "\0" + query + "\0" + topK + "\0" + alpha;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(raw.hashCode());
        }
    }
}
