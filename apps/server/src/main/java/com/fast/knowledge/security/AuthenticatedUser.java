package com.fast.knowledge.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticatedUser {
    private Long userId;
    private String username;
    private String role;
}
