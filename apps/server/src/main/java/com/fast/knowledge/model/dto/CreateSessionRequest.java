package com.fast.knowledge.model.dto;

import lombok.Data;

@Data
public class CreateSessionRequest {
    private Long kbId;
    private String title;
}
