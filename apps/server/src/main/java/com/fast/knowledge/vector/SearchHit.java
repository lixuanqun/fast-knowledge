package com.fast.knowledge.vector;

import lombok.Data;

@Data
public class SearchHit {
    private Long kbId;
    private Long documentId;
    private Long chunkId;
    private String title;
    private String content;
    private double score;
}
