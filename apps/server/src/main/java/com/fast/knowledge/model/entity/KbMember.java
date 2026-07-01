package com.fast.knowledge.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KbMember {
    private Long id;
    private Long kbId;
    private Long userId;
    private String permission;
    private LocalDateTime createdAt;
    private String username;
    private String displayName;
}
