package com.fast.knowledge.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeBase {
    private Long id;
    private String name;
    private String description;
    private Long workspaceId;
    private Long ownerId;
    private String visibility;
    private Double searchAlpha;
    private Integer searchTopK;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
