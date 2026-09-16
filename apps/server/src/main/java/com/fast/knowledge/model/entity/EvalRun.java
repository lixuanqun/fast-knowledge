package com.fast.knowledge.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评测运行：status = RUNNING / DONE / FAILED；
 * metricsJson 为聚合指标（recall@k、MRR、关键词命中率等）。
 */
@Data
@TableName("kb_eval_run")
public class EvalRun {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long datasetId;
    private Long kbId;
    private Integer topK;
    private String status;
    private Integer totalCases;
    /** JSON 对象字符串 */
    private String metricsJson;
    private String error;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
