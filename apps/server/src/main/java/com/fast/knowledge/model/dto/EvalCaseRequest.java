package com.fast.knowledge.model.dto;

import lombok.Data;

import java.util.List;

/**
 * 评测用例请求体 — 期望值用数组承载，服务端序列化为 JSON 字符串入库。
 */
@Data
public class EvalCaseRequest {
    private String question;
    private List<Long> expectedChunkIds;
    private List<String> expectedKeywords;
    private Integer enabled;
}
