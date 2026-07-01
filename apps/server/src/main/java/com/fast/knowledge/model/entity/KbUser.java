package com.fast.knowledge.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KbUser {
    private Long id;
    private String username;
    private String password;
    private String displayName;
    private String role;
    private Integer status;
    private Boolean mustChangePassword;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
