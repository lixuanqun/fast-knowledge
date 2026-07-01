package com.fast.knowledge.model.vo;

import lombok.Data;

@Data
public class DocumentPreviewVO {
    private Long documentId;
    private String title;
    private String fileType;
    private String previewMode;
    private String content;
    private boolean truncated;
    private int contentLength;
}
