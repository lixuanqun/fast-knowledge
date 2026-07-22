package com.fast.knowledge.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class QaHistoryVO {
    private Long id;
    private Long userId;
    private Long kbId;
    private String question;
    private String answer;
    private String sources;
    private Integer sourceCount;
    private LocalDateTime createdAt;
}
