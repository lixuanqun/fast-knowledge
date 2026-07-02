package com.fast.knowledge.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("kb_kb_member")
public class KbMember {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long kbId;
    private Long userId;
    private String permission;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(exist = false)
    private String username;
    @TableField(exist = false)
    private String displayName;
}
