package com.fast.knowledge.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LlmConfigRequest {

    @NotBlank(message = "提供商不能为空")
    @Size(max = 32)
    private String provider;

    @NotBlank(message = "API Base URL 不能为空")
    @Size(max = 512)
    private String baseUrl;

    /** 留空或 ******** 表示不修改已保存的 API Key */
    @Size(max = 512)
    private String apiKey;

    @NotBlank(message = "模型不能为空")
    @Size(max = 128)
    private String model;

    private boolean allowExternal = true;
}
