package com.fast.knowledge.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DocumentChunk {
    private Long id;
    private Long kbId;
    private Long documentId;
    private Integer chunkIndex;
    private String content;
    private Integer tokenCount;
    private LocalDateTime createdAt;
}
