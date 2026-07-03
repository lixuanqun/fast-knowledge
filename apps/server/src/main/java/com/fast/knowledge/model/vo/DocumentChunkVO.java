package com.fast.knowledge.model.vo;

import lombok.Data;

@Data
public class DocumentChunkVO {
    private Long id;
    private Integer chunkIndex;
    private String content;
    private String sectionTitle;
    private Integer tokenCount;
}
