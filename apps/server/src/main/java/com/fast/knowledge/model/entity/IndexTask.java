package com.fast.knowledge.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class IndexTask {
    private Long id;
    private Long documentId;
    private String status;
    private Integer retryCount;
    private String errorMsg;
    private String lockedBy;
    private LocalDateTime lockedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
