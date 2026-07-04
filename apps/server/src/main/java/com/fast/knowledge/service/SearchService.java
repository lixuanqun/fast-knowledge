package com.fast.knowledge.service;

import com.fast.knowledge.model.dto.SearchRequest;
import com.fast.knowledge.model.vo.SearchHitVO;

import java.util.List;

/**
 * 知识库混合检索服务接口。
 */
public interface SearchService {

    /**
     * 按知识库执行混合检索（向量 + 全文），支持缓存与可选 Rerank。
     *
     * @param request 检索请求（含知识库 ID、查询文本、可选过滤条件）
     * @return 排序后的检索命中列表
     */
    List<SearchHitVO> search(SearchRequest request) throws Exception;
}
