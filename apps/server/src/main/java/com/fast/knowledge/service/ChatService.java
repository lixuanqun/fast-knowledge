package com.fast.knowledge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fast.knowledge.audit.AuditActions;
import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.langchain4j.RetrievedContentMapper;
import com.fast.knowledge.langchain4j.assistant.KbChatAssistantFactory;
import com.fast.knowledge.langchain4j.memory.DbChatMemoryStore;
import com.fast.knowledge.mapper.ChatMessageMapper;
import com.fast.knowledge.mapper.ChatSessionMapper;
import com.fast.knowledge.model.dto.ChatMessageRequest;
import com.fast.knowledge.model.entity.ChatMessage;
import com.fast.knowledge.model.entity.ChatSession;
import com.fast.knowledge.model.vo.SearchHitVO;
import com.fast.knowledge.security.UserContext;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.TokenStream;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@Service
public class ChatService {

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final KnowledgeBaseService knowledgeBaseService;
    private final KbChatAssistantFactory kbChatAssistantFactory;
    private final DbChatMemoryStore chatMemoryStore;
    private final ObjectMapper objectMapper;
    private final Executor chatExecutor;
    private final AuditLogService auditLogService;

    public ChatService(ChatSessionMapper chatSessionMapper,
                       ChatMessageMapper chatMessageMapper,
                       KnowledgeBaseService knowledgeBaseService,
                       KbChatAssistantFactory kbChatAssistantFactory,
                       DbChatMemoryStore chatMemoryStore,
                       ObjectMapper objectMapper,
                       @Qualifier("chatExecutor") Executor chatExecutor,
                       AuditLogService auditLogService) {
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.knowledgeBaseService = knowledgeBaseService;
        this.kbChatAssistantFactory = kbChatAssistantFactory;
        this.chatMemoryStore = chatMemoryStore;
        this.objectMapper = objectMapper;
        this.chatExecutor = chatExecutor;
        this.auditLogService = auditLogService;
    }

    public List<ChatSession> listSessions() {
        return chatSessionMapper.findSessionsByUserId(UserContext.currentUserId());
    }

    public ChatSession createSession(Long kbId, String title) {
        return createSession(UserContext.currentUserId(), kbId, title);
    }

    public ChatSession createSession(Long userId, Long kbId, String title) {
        if (kbId != null) {
            knowledgeBaseService.checkReadPermission(knowledgeBaseService.getById(kbId));
        }
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setKbId(kbId);
        session.setTitle(title != null ? title : "新对话");
        chatSessionMapper.insert(session);
        return session;
    }

    public List<ChatMessage> getMessages(Long sessionId) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(UserContext.currentUserId())) {
            throw new BusinessException("会话不存在");
        }
        return chatMessageMapper.findMessagesBySessionId(sessionId);
    }

    public void deleteSession(Long sessionId) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(UserContext.currentUserId())) {
            throw new BusinessException("会话不存在");
        }
        chatMemoryStore.deleteMessages(sessionId);
        chatSessionMapper.deleteById(sessionId);
    }

    public SseEmitter chatStream(ChatMessageRequest request) {
        SseEmitter emitter = new SseEmitter(120000L);
        Long userId = UserContext.currentUserId();

        chatExecutor.execute(() -> {
            try {
                ChatSession session = resolveSession(request, userId);
                Long kbId = request.getKbId() != null ? request.getKbId() : session.getKbId();
                if (kbId != null) {
                    knowledgeBaseService.checkReadPermission(knowledgeBaseService.getById(kbId));
                }

                final ChatSession activeSession = session;
                final List<SearchHitVO> sources = new ArrayList<>();
                TokenStream tokenStream = kbChatAssistantFactory.stream(
                        kbId, activeSession.getId(), request.getMessage());

                tokenStream
                        .onRetrieved((List<Content> contents) -> sources.addAll(RetrievedContentMapper.toSearchHits(contents)))
                        .onPartialResponse(partial -> {
                            try {
                                emitter.send(SseEmitter.event().data(partial));
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        })
                        .onCompleteResponse(response -> {
                            try {
                                if (!sources.isEmpty()) {
                                    chatMemoryStore.attachSources(
                                            activeSession.getId(),
                                            objectMapper.writeValueAsString(sources));
                                }
                                chatSessionMapper.touchSession(
                                        activeSession.getId(), java.time.LocalDateTime.now());

                                auditLogService.log(AuditActions.CHAT, "SESSION", activeSession.getId(),
                                        "kbId=" + kbId + ", message=" + truncate(request.getMessage(), 200));

                                Map<String, Object> done = new HashMap<>();
                                done.put("sessionId", activeSession.getId());
                                done.put("sources", sources);
                                emitter.send(SseEmitter.event().name("done")
                                        .data(objectMapper.writeValueAsString(done)));
                                emitter.complete();
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        })
                        .onError(error -> {
                            try {
                                emitter.send(SseEmitter.event().name("error").data(error.getMessage()));
                            } catch (IOException ignored) {
                            }
                            emitter.completeWithError(error);
                        })
                        .start();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                } catch (IOException ignored) {
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private ChatSession resolveSession(ChatMessageRequest request, Long userId) {
        if (request.getSessionId() != null) {
            ChatSession session = chatSessionMapper.selectById(request.getSessionId());
            if (session == null || !session.getUserId().equals(userId)) {
                throw new BusinessException("会话不存在");
            }
            if (request.getKbId() != null) {
                session.setKbId(request.getKbId());
                chatSessionMapper.updateById(session);
            }
            return session;
        }
        return createSession(userId, request.getKbId(), truncate(request.getMessage(), 20));
    }

    private String truncate(String s, int len) {
        if (s == null) {
            return "新对话";
        }
        return s.length() <= len ? s : s.substring(0, len) + "...";
    }
}
