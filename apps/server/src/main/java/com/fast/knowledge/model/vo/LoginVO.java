package com.fast.knowledge.model.vo;

import lombok.Data;

@Data
public class LoginVO {
    private String token;
    private Long userId;
    private String username;
    private String displayName;
    private String role;
    private Boolean mustChangePassword;
}
