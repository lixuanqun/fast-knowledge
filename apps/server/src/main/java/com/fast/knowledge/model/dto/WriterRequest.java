package com.fast.knowledge.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WriterRequest {
    private Long kbId;
    @NotBlank(message = "写作主题不能为空")
    private String topic;
    private String outline;
    private String style;
    private Integer wordCount;
}
