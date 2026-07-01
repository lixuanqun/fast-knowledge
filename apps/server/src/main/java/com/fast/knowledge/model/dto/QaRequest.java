package com.fast.knowledge.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class QaRequest {
    @NotNull(message = "请指定知识库")
    private Long kbId;
    @NotBlank(message = "问题不能为空")
    private String question;
}
