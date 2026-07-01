package com.fast.knowledge.model.dto;

import lombok.Data;

@Data
public class SearchRequest {
    private Long kbId;
    private String query;
    private Integer topK;
    private Double alpha;
}
