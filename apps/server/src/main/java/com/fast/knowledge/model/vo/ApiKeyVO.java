package com.fast.knowledge.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ApiKeyVO {
    private Long id;
    private String name;
    private String keyPrefix;
    private Long kbId;
    private Long userId;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime lastUsedAt;
}
