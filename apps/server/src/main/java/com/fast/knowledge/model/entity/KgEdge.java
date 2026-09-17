package com.fast.knowledge.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** WP7 知识图谱关系边（kb 级唯一：kb_id + src + dst + relation） */
@Data
@TableName("kg_edge")
public class KgEdge {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long kbId;
    private Long srcId;
    private Long dstId;
    private String relation;
    /** 抽取该关系时依据的文档（删除文档时清理对应边） */
    private Long evidenceDocId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
