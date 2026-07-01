package com.fast.knowledge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.mapper.ChatMapper;
import com.fast.knowledge.model.dto.ChatMessageRequest;
import com.fast.knowledge.model.entity.ChatMessage;
import com.fast.knowledge.model.entity.ChatSession;
import com.fast.knowledge.model.vo.RagContextVO;
import com.fast.knowledge.model.vo.SearchHitVO;
import com.fast.knowledge.security.UserContext;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
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

    private final ChatMapper chatMapper;
    private final RagService ragService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final StreamingChatModel streamingChatModel;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Executor chatExecutor;

    public ChatService(ChatMapper chatMapper,
                       RagService ragService,
                       KnowledgeBaseService knowledgeBaseService,
                       StreamingChatModel streamingChatModel,
                       @org.springframework.beans.factory.annotation.Qualifier("indexExecutor") Executor chatExecutor) {
        this.chatMapper = chatMapper;
        this.ragService = ragService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.streamingChatModel = streamingChatModel;
        this.chatExecutor = chatExecutor;
    }

    public List<ChatSession> listSessions() {
        return chatMapper.findSessionsByUserId(UserContext.currentUserId());
    }

    public ChatSession createSession(Long kbId, String title) {
        if (kbId != null) {
            knowledgeBaseService.checkReadPermission(knowledgeBaseService.getById(kbId));
        }
        ChatSession session = new ChatSession();
        session.setUserId(UserContext.currentUserId());
        session.setKbId(kbId);
        session.setTitle(title != null ? title : "新对话");
        chatMapper.insertSession(session);
        return session;
    }

    public List<ChatMessage> getMessages(Long sessionId) {
        ChatSession session = chatMapper.findSessionById(sessionId);
        if (session == null || !session.getUserId().equals(UserContext.currentUserId())) {
            throw new BusinessException("会话不存在");
        }
        return chatMapper.findMessagesBySessionId(sessionId);
    }

    public void deleteSession(Long sessionId) {
        ChatSession session = chatMapper.findSessionById(sessionId);
        if (session == null || !session.getUserId().equals(UserContext.currentUserId())) {
            throw new BusinessException("会话不存在");
        }
        chatMapper.deleteMessagesBySessionId(sessionId);
        chatMapper.deleteSession(sessionId);
    }

    public SseEmitter chatStream(ChatMessageRequest request) {
        SseEmitter emitter = new SseEmitter(120000L);
        Long userId = UserContext.currentUserId();
        chatExecutor.execute(() -> {
            ChatSession session = null;
            try {
                session = resolveSession(request, userId);
                Long kbId = request.getKbId() != null ? request.getKbId() : session.getKbId();

                RagContextVO rag = kbId != null
                        ? ragService.retrieve(kbId, request.getMessage())
                        : new RagContextVO("", List.of());

                List<dev.langchain4j.data.message.ChatMessage> messages =
                        buildPromptMessages(session.getId(), rag.getContext(), request.getMessage());

                ChatMessage userMsg = new ChatMessage();
                userMsg.setSessionId(session.getId());
                userMsg.setRole("user");
                userMsg.setContent(request.getMessage());
                chatMapper.insertMessage(userMsg);
                chatMapper.touchSession(session.getId());

                final ChatSession activeSession = session;
                final List<SearchHitVO> sources = rag.getSources();
                StringBuilder answerBuilder = new StringBuilder();
                streamingChatModel.chat(messages, new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String partialResponse) {
                        answerBuilder.append(partialResponse);
                        try {
                            emitter.send(SseEmitter.event().data(partialResponse));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    @Override
                    public void onCompleteResponse(ChatResponse response) {
                        try {
                            String answer = answerBuilder.length() > 0
                                    ? answerBuilder.toString()
                                    : response.aiMessage().text();

                            ChatMessage assistantMsg = new ChatMessage();
                            assistantMsg.setSessionId(activeSession.getId());
                            assistantMsg.setRole("assistant");
                            assistantMsg.setContent(answer);
                            if (!sources.isEmpty()) {
                                assistantMsg.setSources(objectMapper.writeValueAsString(sources));
                            }
                            chatMapper.insertMessage(assistantMsg);
                            chatMapper.touchSession(activeSession.getId());

                            Map<String, Object> done = new HashMap<>();
                            done.put("sessionId", activeSession.getId());
                            done.put("sources", sources);
                            emitter.send(SseEmitter.event().name("done")
                                    .data(objectMapper.writeValueAsString(done)));
                            emitter.complete();
                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        try {
                            emitter.send(SseEmitter.event().name("error").data(error.getMessage()));
                        } catch (IOException ignored) {
                        }
                        emitter.completeWithError(error);
                    }
                });
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
            ChatSession session = chatMapper.findSessionById(request.getSessionId());
            if (session == null || !session.getUserId().equals(userId)) {
                throw new BusinessException("会话不存在");
            }
            if (request.getKbId() != null) {
                session.setKbId(request.getKbId());
                chatMapper.updateSession(session);
            }
            return session;
        }
        return createSession(request.getKbId(), truncate(request.getMessage(), 20));
    }

    private List<dev.langchain4j.data.message.ChatMessage> buildPromptMessages(
            Long sessionId, String context, String question) {
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(
                "你是 Fast Knowledge 快速知识库对话助手。结合参考资料与对话历史回答问题，使用简体中文。若资料不足请如实说明，不要编造。"));
        if (!context.isBlank()) {
            messages.add(SystemMessage.from("参考资料：\n" + context));
        }
        List<ChatMessage> history = chatMapper.findMessagesBySessionId(sessionId);
        int start = Math.max(0, history.size() - 10);
        for (int i = start; i < history.size(); i++) {
            ChatMessage m = history.get(i);
            if ("user".equals(m.getRole())) {
                messages.add(UserMessage.from(m.getContent()));
            } else if ("assistant".equals(m.getRole())) {
                messages.add(AiMessage.from(m.getContent()));
            }
        }
        messages.add(UserMessage.from(question));
        return messages;
    }

    private String truncate(String s, int len) {
        if (s == null) {
            return "新对话";
        }
        return s.length() <= len ? s : s.substring(0, len) + "...";
    }
}
