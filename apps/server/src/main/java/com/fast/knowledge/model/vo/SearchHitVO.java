package com.fast.knowledge.model.vo;

import lombok.Data;

@Data
public class SearchHitVO {
    private Long chunkId;
    private Long documentId;
    private String documentTitle;
    private String docType;
    private String docNo;
    private String section;
    private String content;
    private double score;
}
