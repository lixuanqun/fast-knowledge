package com.fast.knowledge.service;

/**
 * 多轮对话查询改写 — 基于对话历史消解指代、补全省略。
 */
public interface QueryRewriter {

    /**
     * 改写当前查询，消解指代（它/这个/那个等），使其成为完整独立的检索查询。
     * 首轮对话或改写失败时返回原始消息。
     *
     * @param sessionId      会话 ID
     * @param currentMessage 用户当前消息
     * @return 改写后的查询
     */
    String rewrite(Long sessionId, String currentMessage);
}
