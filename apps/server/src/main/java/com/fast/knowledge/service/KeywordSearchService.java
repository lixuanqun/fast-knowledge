package com.fast.knowledge.service;

import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.mapper.DocumentChunkMapper;
import com.fast.knowledge.model.vo.SearchHitVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 混合检索关键词支路 — MySQL FULLTEXT ngram 全文召回。
 *
 * <p>归一化：将 MySQL 相关度（0~10+ 量级）按本次召回集合的最大值缩放到 [0,1]，
 * 与向量余弦分同尺度，供加权融合与 UI 相关度展示复用。
 * 方言异常（H2 等不支持 FULLTEXT 的库误开启）时降级为空结果，检索退化为纯向量。
 */
@Slf4j
@Service
public class KeywordSearchService {

    private final DocumentChunkMapper chunkMapper;
    private final KnowledgeProperties properties;
    private volatile boolean dialectWarned;

    public KeywordSearchService(DocumentChunkMapper chunkMapper, KnowledgeProperties properties) {
        this.chunkMapper = chunkMapper;
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.getSearch().isKeywordEnabled();
    }

    /** 全文召回，分数归一化到 [0,1]；不可用或无命中返回空列表 */
    public List<SearchHitVO> recall(Long kbId, String query, String docType, int limit) {
        if (!isEnabled()) {
            return List.of();
        }
        try {
            List<SearchHitVO> hits = chunkMapper.fulltextSearch(kbId, query, docType, limit);
            double max = hits.stream().mapToDouble(SearchHitVO::getScore).max().orElse(0);
            if (max > 0) {
                for (SearchHitVO hit : hits) {
                    hit.setScore(hit.getScore() / max);
                }
            }
            return hits;
        } catch (Exception e) {
            if (!dialectWarned) {
                dialectWarned = true;
                log.warn("关键词支路不可用（当前数据库可能不支持 FULLTEXT），检索退化为纯向量: {}", e.getMessage());
            }
            return List.of();
        }
    }
}
