package com.fast.knowledge.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SearchRequest {
    @NotNull(message = "请指定知识库")
    private Long kbId;
    @NotBlank(message = "检索关键词不能为空")
    private String query;
    private Integer topK;
    private Double alpha;
}
