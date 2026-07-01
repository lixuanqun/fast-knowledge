package com.fast.knowledge.model.vo;

import lombok.Data;

import java.util.List;

@Data
public class QaResponseVO {
    private String answer;
    private List<SearchHitVO> sources;
}
