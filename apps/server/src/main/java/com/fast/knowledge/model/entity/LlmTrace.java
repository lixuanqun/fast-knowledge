package com.fast.knowledge.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** WP10 LLM 调用链路 trace（用量看板数据源） */
@Data
@TableName("kb_llm_trace")
public class LlmTrace {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 场景：chat / qa / writer / wiki / chunk_context / rerank / kg_extract / agentic_decompose / agentic_critique / ocr */
    private String scene;
    private String provider;
    private String model;
    private Integer latencyMs;
    private Integer promptChars;
    private Integer responseChars;
    /** OK / ERROR */
    private String status;
    private String error;
    /** 关联 ID（如 sessionId / docId / runId，便于串起检索→生成全链路） */
    private String correlationId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
