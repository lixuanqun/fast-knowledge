package com.fast.knowledge.ai.port;

import com.fast.knowledge.model.vo.SearchHitVO;

import java.util.List;

/**
 * 重排端口 — 对召回候选按查询相关性重排序。
 */
public interface RerankPort {

    /** 重排模型是否就绪（决定召回是否过取） */
    boolean isActive();

    /** 重排开启时的候选过取数量 */
    int candidateCount(int topK);

    List<SearchHitVO> rerank(String query, List<SearchHitVO> hits, int topK);
}
