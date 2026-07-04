package com.fast.knowledge.service;

import com.fast.knowledge.model.dto.QaRequest;
import com.fast.knowledge.model.vo.QaResponseVO;
import com.fast.knowledge.model.vo.RagContextVO;

/**
 * RAG 问答服务接口。
 */
public interface RagService {

    /**
     * 单次 RAG 问答：检索 + 拼接上下文 + LLM 生成回答。
     *
     * @param request 问答请求（含知识库 ID 与问题）
     * @return 问答响应（含回答文本与引用来源）
     */
    QaResponseVO ask(QaRequest request) throws Exception;

    /**
     * 检索指定知识库并组装结构化上下文。
     *
     * @param kbId  知识库 ID
     * @param query 查询文本
     * @return 包含格式化上下文与来源列表的上下文对象
     */
    RagContextVO retrieve(Long kbId, String query) throws Exception;

    /**
     * 构建拼接后的纯文本上下文（用于下游对话或写作）。
     *
     * @param kbId  知识库 ID
     * @param query 查询文本
     * @return 拼接后的参考资料文本
     */
    String buildContext(Long kbId, String query) throws Exception;
}
