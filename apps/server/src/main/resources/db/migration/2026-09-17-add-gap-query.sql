-- WP9 知识缺口采集表（幂等）
CREATE TABLE IF NOT EXISTS kb_gap_query (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    kb_id       BIGINT       NOT NULL,
    query       VARCHAR(512) NOT NULL,
    hit_count   INT          NOT NULL DEFAULT 0,
    status      VARCHAR(32)  NOT NULL DEFAULT 'OPEN',
    cluster_id  INT          NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_gap_kb_status (kb_id, status),
    KEY idx_gap_cluster (cluster_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;
