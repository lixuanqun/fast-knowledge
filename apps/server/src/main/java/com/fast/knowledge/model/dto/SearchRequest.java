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
    /** 按文档业务类型过滤（POLICY/PROCESS 等） */
    private String docType;
    /** @deprecated HYBRID RRF 不使用，请求体忽略 */
    @Deprecated
    private Double alpha;
}
