package com.fast.knowledge.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatMessageRequest {
    private Long sessionId;
    private Long kbId;
    @NotBlank(message = "消息内容不能为空")
    private String message;
}
