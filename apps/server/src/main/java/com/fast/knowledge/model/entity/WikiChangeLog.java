package com.fast.knowledge.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Wiki 页变更日志 — Wiki 维护 Agent 每次编译/合并落一行，供审计与 diff 回溯。
 */
@Data
@TableName("kb_wiki_change_log")
public class WikiChangeLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long kbId;
    private Long pageId;
    private Integer fromVersion;
    private Integer toVersion;
    /** MERGE（增量合并）| COMPILE（全量编译） */
    private String changeType;
    /** 变更摘要（规则拼接 + Lint 结论） */
    private String summary;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
