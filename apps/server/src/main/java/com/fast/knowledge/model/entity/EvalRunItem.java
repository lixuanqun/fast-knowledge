package com.fast.knowledge.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评测运行明细（单用例结果）：
 * firstHitRank = 首个期望 chunk 的命中名次（1 起，未命中为 null）；
 * recall = 期望 chunk 命中比例；keywordHit = 关键词全部命中（1/0，无关键词为 null）。
 */
@Data
@TableName("kb_eval_run_item")
public class EvalRunItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long runId;
    private Long caseId;
    private String question;
    private Integer firstHitRank;
    private Double recall;
    private Integer keywordHit;
    private Integer latencyMs;
    /** JSON 数组字符串：实际召回的 chunkId 列表 */
    private String hitChunkIds;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
