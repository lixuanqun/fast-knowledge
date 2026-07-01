package com.fast.knowledge.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatSession {
    private Long id;
    private Long userId;
    private Long kbId;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
