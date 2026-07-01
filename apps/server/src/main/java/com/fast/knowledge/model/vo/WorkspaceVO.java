package com.fast.knowledge.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkspaceVO {
    private Long id;
    private String name;
    private Long ownerId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
