package com.fast.knowledge.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SaveDocumentRequest {
    @NotNull(message = "请指定知识库")
    private Long kbId;
    private String title;
    @NotBlank(message = "文档内容不能为空")
    private String content;
}
