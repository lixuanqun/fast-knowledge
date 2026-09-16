package com.fast.knowledge.ai.port;

import java.util.List;

/**
 * 分块上下文端口 — WP1 上下文化分块（Contextual Retrieval）。
 * 实现方为每个 chunk 生成 1-2 句上下文前缀（说明该片段在文档中的位置与主题），
 * 前缀与正文一起参与向量化，用于弥补长文档切块后脱离上下文导致的检索失败。
 * 失败时实现方返回空串占位，调用方按无前缀降级，不阻塞索引。
 */
public interface ChunkContextPort {

    /**
     * @param docTitle        文档标题
     * @param fullTextPreview 全文摘录（供 LLM 理解文档全貌，可为空串）
     * @param chunks          按顺序排列的分块原文
     * @return 与 chunks 等长的上下文前缀列表（元素可为空串）
     */
    List<String> generateContexts(String docTitle, String fullTextPreview, List<String> chunks);
}
