package com.fast.knowledge.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KbMemberRequest {
    @NotBlank
    private String username;
    private String permission = "READ";
}
