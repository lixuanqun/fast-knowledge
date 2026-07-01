package com.fast.knowledge.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SetupRequest {
    @NotBlank(message = "实例名称不能为空")
    @Size(max = 128)
    private String instanceName;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 64)
    private String newPassword;
}
