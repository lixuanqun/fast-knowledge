package com.fast.knowledge.model.dto;

import lombok.Data;

@Data
public class WriterRequest {
    private Long kbId;
    private String topic;
    private String outline;
    private String style;
    private Integer wordCount;
}
