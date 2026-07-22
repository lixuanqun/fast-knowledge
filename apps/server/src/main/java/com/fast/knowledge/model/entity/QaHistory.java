package com.fast.knowledge.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("kb_qa_history")
public class QaHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long kbId;
    private String question;
    private String answer;
    /** JSON 数组：SearchHitVO 列表 */
    private String sources;
    private Integer sourceCount;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
