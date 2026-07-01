package com.fast.knowledge.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KbDocument {
    private Long id;
    private Long kbId;
    private String title;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String filePath;
    private String indexStatus;
    private String indexError;
    private Integer chunkCount;
    private Integer enabled;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
