package com.fast.knowledge.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class KbMemberRequest {
    @NotBlank
    private String username;
    @Pattern(regexp = "READ|WRITE|ADMIN", message = "权限无效，仅支持 READ、WRITE 或 ADMIN")
    private String permission = "READ";
}
