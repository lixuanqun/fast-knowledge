package com.fast.knowledge.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Workspace {
    private Long id;
    private String name;
    private Long ownerId;
    private String settings;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
