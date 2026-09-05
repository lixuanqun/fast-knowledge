package com.fast.knowledge.ai.port;

import com.fast.knowledge.model.vo.SearchHitVO;

import java.util.List;

/**
 * 多轮对话端口 — 封装会话记忆、知识库检索增强与来源溯源。
 * 实现方负责助手实例装配、生命周期与流式启动。
 */
public interface ConversationPort {

    /** 启动一轮流式对话；回调在实现方线程上触发 */
    void streamConversation(Long kbId, Long sessionId, String message, ConversationHandler handler);

    /** 索引或配置变更后，失效按知识库缓存的助手实例 */
    void evictAssistant(Long kbId);

    /** 删除会话记忆 */
    void deleteMemory(Long sessionId);

    /** 附加会话溯源信息（JSON） */
    void attachSources(Long sessionId, String sourcesJson);

    interface ConversationHandler {
        /** 检索命中的来源（增强器回调） */
        void onRetrieved(List<SearchHitVO> sources);

        void onPartial(String token);

        void onComplete();

        void onError(Throwable error);
    }
}
