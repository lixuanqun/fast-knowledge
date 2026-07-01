package com.fast.knowledge.model.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateUserRequest {
    private String displayName;

    @Pattern(regexp = "ADMIN|USER", message = "角色无效，仅支持 ADMIN 或 USER")
    private String role;

    private Integer status;
}
