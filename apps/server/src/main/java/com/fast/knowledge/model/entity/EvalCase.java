package com.fast.knowledge.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评测用例：question 为测试问题；
 * expectedChunkIds 为期望命中的 chunk（JSON 数组，评估 recall/MRR）；
 * expectedKeywords 为期望出现在检索内容中的关键词（JSON 数组，评估关键词命中率）。
 */
@Data
@TableName("kb_eval_case")
public class EvalCase {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long datasetId;
    private String question;
    /** JSON 数组字符串，如 [101,102] */
    private String expectedChunkIds;
    /** JSON 数组字符串，如 ["冬季巡检","供暖"] */
    private String expectedKeywords;
    private Integer enabled;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
