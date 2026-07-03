package com.fast.knowledge.model.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class DocumentMetadataRequest {
    private String docType;
    private String docNo;
    private LocalDate effectiveDate;
    private LocalDate expireDate;
    private String department;
    /** 逗号分隔标签 */
    private String tags;
}
