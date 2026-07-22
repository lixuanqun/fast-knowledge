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
    /**
     * 是否参与检索：1=是，0=否（作废/下架）。
     * null 表示不修改。
     */
    private Integer enabled;
    /** true 时清空生效日期 */
    private Boolean clearEffectiveDate;
    /** true 时清空失效日期 */
    private Boolean clearExpireDate;
}
