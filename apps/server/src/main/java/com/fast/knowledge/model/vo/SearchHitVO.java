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
    /** WP5 溯源：所在页码（OCR/分页文档） */
    private Integer pageNo;
    /** WP5 溯源：锚点类型 text / table */
    private String anchorType;
    private String content;
    private double score;
}
