package com.fast.knowledge.controller;

import com.fast.knowledge.common.ApiResponse;
import com.fast.knowledge.model.dto.ChatMessageRequest;
import com.fast.knowledge.model.dto.CreateSessionRequest;
import com.fast.knowledge.model.entity.ChatMessage;
import com.fast.knowledge.model.entity.ChatSession;
import com.fast.knowledge.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/sessions")
    public ApiResponse<List<ChatSession>> sessions() {
        return ApiResponse.ok(chatService.listSessions());
    }

    @PostMapping("/sessions")
    public ApiResponse<ChatSession> createSession(@Valid @RequestBody CreateSessionRequest request) {
        return ApiResponse.ok(chatService.createSession(request.getKbId(), request.getTitle()));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResponse<List<ChatMessage>> messages(@PathVariable Long sessionId) {
        return ApiResponse.ok(chatService.getMessages(sessionId));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Void> deleteSession(@PathVariable Long sessionId) {
        chatService.deleteSession(sessionId);
        return ApiResponse.ok();
    }

    @PostMapping(value = "/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatMessageRequest request) {
        return chatService.chatStream(request);
    }
}
