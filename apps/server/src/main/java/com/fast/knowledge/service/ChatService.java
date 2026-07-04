package com.fast.knowledge.service;

import com.fast.knowledge.model.dto.ChatMessageRequest;
import com.fast.knowledge.model.entity.ChatMessage;
import com.fast.knowledge.model.entity.ChatSession;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 多轮对话服务接口。
 */
public interface ChatService {

    /** 获取当前用户的会话列表 */
    List<ChatSession> listSessions();

    /** 为当前用户创建会话 */
    ChatSession createSession(Long kbId, String title);

    /** 为指定用户创建会话 */
    ChatSession createSession(Long userId, Long kbId, String title);

    /** 获取会话历史消息 */
    List<ChatMessage> getMessages(Long sessionId);

    /** 删除会话及其消息 */
    void deleteSession(Long sessionId);

    /** 发起流式对话（SSE） */
    SseEmitter chatStream(ChatMessageRequest request);
}
