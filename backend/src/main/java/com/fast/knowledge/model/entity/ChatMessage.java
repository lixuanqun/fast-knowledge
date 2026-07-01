package com.fast.knowledge.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatMessage {
    private Long id;
    private Long sessionId;
    private String role;
    private String content;
    private String sources;
    private LocalDateTime createdAt;
}
