-- WP1 上下文化分块：kb_document_chunk 增加上下文前缀列（幂等：列存在时跳过）
-- MySQL 5.7 无 IF NOT EXISTS，通过 information_schema 判断
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE kb_document_chunk ADD COLUMN context_prefix VARCHAR(512) NULL AFTER section_title',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_document_chunk' AND COLUMN_NAME = 'context_prefix'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- WP5 引用溯源：分块页码与锚点类型
SET @ddl2 = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE kb_document_chunk ADD COLUMN page_no INT NULL AFTER context_prefix, ADD COLUMN anchor_type VARCHAR(16) NULL AFTER page_no',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_document_chunk' AND COLUMN_NAME = 'page_no'
);
PREPARE stmt2 FROM @ddl2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;
