package com.fast.knowledge.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KnowledgeBaseRequest {
    @NotBlank
    private String name;
    private String description;
    private String visibility = "PRIVATE";
    private Double searchAlpha;
    private Integer searchTopK;
}
