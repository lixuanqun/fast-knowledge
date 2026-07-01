package com.fast.knowledge.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class RagContextVO {
    private String context;
    private List<SearchHitVO> sources;
}
