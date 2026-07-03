package com.fast.knowledge.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateApiKeyRequest {
    @NotBlank(message = "名称不能为空")
    private String name;
    /** 限定知识库；空表示继承用户全部可读知识库 */
    private Long kbId;
    @NotNull(message = "请指定绑定用户")
    private Long userId;
}
