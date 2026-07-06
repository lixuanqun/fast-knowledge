package com.fast.knowledge.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fast.knowledge.audit.AuditActions;
import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.common.SseEmitterHelper;
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
import com.fast.knowledge.service.AuditLogService;
import com.fast.knowledge.service.ChatService;
import com.fast.knowledge.service.KnowledgeBaseService;
import com.fast.knowledge.service.MetricsService;
import com.fast.knowledge.service.QueryRewriter;
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
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ChatServiceImpl implements ChatService {

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final KnowledgeBaseService knowledgeBaseService;
    private final KbChatAssistantFactory kbChatAssistantFactory;
    private final DbChatMemoryStore chatMemoryStore;
    private final ObjectMapper objectMapper;
    private final Executor chatExecutor;
    private final AuditLogService auditLogService;
    private final QueryRewriter queryRewriter;
    private final MetricsService metricsService;

    public ChatServiceImpl(ChatSessionMapper chatSessionMapper,
                           ChatMessageMapper chatMessageMapper,
                           KnowledgeBaseService knowledgeBaseService,
                           KbChatAssistantFactory kbChatAssistantFactory,
                           DbChatMemoryStore chatMemoryStore,
                           ObjectMapper objectMapper,
                           @Qualifier("chatExecutor") Executor chatExecutor,
                           AuditLogService auditLogService,
                           QueryRewriter queryRewriter,
                           MetricsService metricsService) {
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.knowledgeBaseService = knowledgeBaseService;
        this.kbChatAssistantFactory = kbChatAssistantFactory;
        this.chatMemoryStore = chatMemoryStore;
        this.objectMapper = objectMapper;
        this.chatExecutor = chatExecutor;
        this.auditLogService = auditLogService;
        this.queryRewriter = queryRewriter;
        this.metricsService = metricsService;
    }

    @Override
    public List<ChatSession> listSessions() {
        return chatSessionMapper.findSessionsByUserId(UserContext.currentUserId());
    }

    @Override
    public ChatSession createSession(Long kbId, String title) {
        return createSession(UserContext.currentUserId(), kbId, title);
    }

    @Override
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

    @Override
    public List<ChatMessage> getMessages(Long sessionId) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(UserContext.currentUserId())) {
            throw new BusinessException("会话不存在");
        }
        return chatMessageMapper.findMessagesBySessionId(sessionId);
    }

    @Override
    public void deleteSession(Long sessionId) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(UserContext.currentUserId())) {
            throw new BusinessException("会话不存在");
        }
        chatMemoryStore.deleteMessages(sessionId);
        chatSessionMapper.deleteById(sessionId);
    }

    @Override
    public SseEmitter chatStream(ChatMessageRequest request) {
        SseEmitter emitter = SseEmitterHelper.create(SseEmitterHelper.TIMEOUT_CHAT);
        Long userId = UserContext.currentUserId();

        chatExecutor.execute(UserContext.wrap(() -> {
            long startTime = System.currentTimeMillis();
            try {
                ChatSession session = resolveSession(request, userId);
                Long kbId = request.getKbId() != null ? request.getKbId() : session.getKbId();
                if (kbId != null) {
                    knowledgeBaseService.checkReadPermission(knowledgeBaseService.getById(kbId));
                }

                final ChatSession activeSession = session;

                // Query rewrite: resolve pronouns and ellipsis before retrieval
                String originalMessage = request.getMessage();
                String rewrittenMessage = queryRewriter.rewrite(activeSession.getId(), originalMessage);
                boolean wasRewritten = !originalMessage.equals(rewrittenMessage);
                metricsService.countQueryRewrite(wasRewritten);

                final java.util.List<SearchHitVO> sources = new ArrayList<>();
                final AtomicBoolean firstTokenRecorded = new AtomicBoolean(false);
                TokenStream tokenStream = kbChatAssistantFactory.stream(
                        kbId, activeSession.getId(), rewrittenMessage);

                tokenStream
                        .onRetrieved((List<Content> contents) -> sources.addAll(RetrievedContentMapper.toSearchHits(contents)))
                        .onPartialResponse(partial -> {
                            try {
                                if (firstTokenRecorded.compareAndSet(false, true)) {
                                    long latency = System.currentTimeMillis() - startTime;
                                    metricsService.recordFirstTokenLatency(latency);
                                }
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
                                        "kbId=" + kbId + ", message=" + truncate(originalMessage, 200));

                                Map<String, Object> done = new HashMap<>();
                                done.put("sessionId", activeSession.getId());
                                done.put("sources", sources);
                                SseEmitterHelper.sendNamed(emitter, "done", objectMapper.writeValueAsString(done));
                                emitter.complete();
                            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                                emitter.completeWithError(e);
                            }
                        })
                        .onError(error -> SseEmitterHelper.sendError(emitter, error.getMessage()))
                        .start();

                metricsService.countLlmCall("chat");
            } catch (Exception e) {
                log.error("Chat stream failed sessionId={}", resolveSessionIdSafely(request), e);
                SseEmitterHelper.sendError(emitter, e.getMessage());
            }
        }));

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

    private Long resolveSessionIdSafely(ChatMessageRequest request) {
        return request != null ? request.getSessionId() : null;
    }

    private String truncate(String s, int len) {
        if (s == null) {
            return "新对话";
        }
        return s.length() <= len ? s : s.substring(0, len) + "...";
    }
}
