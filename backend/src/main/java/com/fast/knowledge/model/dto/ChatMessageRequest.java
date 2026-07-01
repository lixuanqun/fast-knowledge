package com.fast.knowledge.model.dto;

import lombok.Data;

@Data
public class ChatMessageRequest {
    private Long sessionId;
    private Long kbId;
    private String message;
}
