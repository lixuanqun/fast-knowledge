package com.fast.knowledge.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class KnowledgeBaseRequest {
    @NotBlank
    private String name;
    private String description;
    @Pattern(regexp = "PRIVATE|PUBLIC", message = "可见性无效，仅支持 PRIVATE 或 PUBLIC")
    private String visibility = "PRIVATE";
    /** @deprecated HYBRID RRF 不使用，仅数据库兼容保留 */
    @Deprecated
    private Double searchAlpha;
    private Integer searchTopK;
}
