package com.fast.knowledge.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ApiKeyCreatedVO {
    private Long id;
    private String name;
    private Long kbId;
    private Long userId;
    /** 明文密钥，仅创建时返回一次 */
    private String apiKey;
    private LocalDateTime createdAt;
}
