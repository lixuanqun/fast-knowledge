package com.fast.knowledge.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** WP9 无结果/低分检索查询缺口（按 query 累计，运营聚类后指导补文档） */
@Data
@TableName("kb_gap_query")
public class KbGapQuery {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long kbId;
    private String query;
    /** 命中数（0 表示无结果，<3 表示低分） */
    private Integer hitCount;
    /** OPEN=待处理 / ADDRESSED=已补充文档 / IGNORED=已忽略 */
    private String status;
    /** 聚类 ID（相似 query 分到同组） */
    private Integer clusterId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
