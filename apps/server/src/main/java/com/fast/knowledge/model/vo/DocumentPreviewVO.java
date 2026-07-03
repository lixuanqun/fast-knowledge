package com.fast.knowledge.model.vo;

import lombok.Data;

@Data
public class DocumentPreviewVO {
    private Long documentId;
    private String title;
    private String fileType;
    private String docType;
    private String docNo;
    private String previewMode;
    private String content;
    private String highlightSnippet;
    private boolean truncated;
    private int contentLength;
}
