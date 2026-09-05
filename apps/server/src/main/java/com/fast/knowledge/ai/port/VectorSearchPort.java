package com.fast.knowledge.ai.port;

import com.fast.knowledge.model.vo.SearchHitVO;

import java.util.List;

/**
 * 向量检索端口 — 知识库级混合检索（向量 + 全文）。
 * 查询向量由 EmbeddingProvider 产出后传入，便于调用方分段计时。
 */
public interface VectorSearchPort {

    /** @param fetchK 过取数量（含召回策略）；@param docType 可选文档类型过滤 */
    List<SearchHitVO> search(Long kbId, float[] queryVector, String query, int fetchK, String docType);
}
