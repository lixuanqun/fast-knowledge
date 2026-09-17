package com.fast.knowledge.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("kb_document_chunk")
public class DocumentChunk {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long kbId;
    private Long documentId;
    private Integer chunkIndex;
    private String content;
    private String sectionTitle;
    /** WP1 上下文化分块：LLM 生成的上下文前缀（与正文一起向量化），未启用为 null */
    private String contextPrefix;
    /** WP5 溯源：所在页码（OCR 文档按页标记推算），非分页文档为 null */
    private Integer pageNo;
    /** WP5 溯源：内容锚点类型 text / table */
    private String anchorType;
    private Integer tokenCount;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
