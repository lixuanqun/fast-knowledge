-- WP7 知识图谱表（幂等：CREATE TABLE IF NOT EXISTS 已在 schema-mysql.sql，本文件供存量库升级）
CREATE TABLE IF NOT EXISTS kg_entity (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    kb_id       BIGINT       NOT NULL,
    name        VARCHAR(128) NOT NULL,
    type        VARCHAR(32)  NOT NULL DEFAULT '其他',
    description VARCHAR(512) DEFAULT '',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_kg_entity (kb_id, name),
    KEY idx_kg_entity_kb (kb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS kg_edge (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    kb_id            BIGINT       NOT NULL,
    src_id           BIGINT       NOT NULL,
    dst_id           BIGINT       NOT NULL,
    relation         VARCHAR(128) NOT NULL DEFAULT '相关',
    evidence_doc_id  BIGINT       NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_kg_edge (kb_id, src_id, dst_id, relation),
    KEY idx_kg_edge_src (kb_id, src_id),
    KEY idx_kg_edge_dst (kb_id, dst_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;
