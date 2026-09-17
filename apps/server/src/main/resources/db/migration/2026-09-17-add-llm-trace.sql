
-- WP10 LLM 用量可观测：调用链路 trace（scene/provider/model/latency/tokens/cost/correlation_id）
CREATE TABLE IF NOT EXISTS kb_llm_trace (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    scene           VARCHAR(64)  NOT NULL,
    provider        VARCHAR(64)  NOT NULL,
    model           VARCHAR(128) NOT NULL,
    latency_ms      INT          NOT NULL DEFAULT 0,
    prompt_chars    INT          NOT NULL DEFAULT 0,
    response_chars  INT          NOT NULL DEFAULT 0,
    status          VARCHAR(16)  NOT NULL DEFAULT 'OK',
    error           VARCHAR(512) NULL,
    correlation_id  VARCHAR(64)  NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_llm_trace_scene (scene, created_at),
    KEY idx_llm_trace_corr (correlation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;
