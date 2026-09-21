-- Fast Knowledge baseline schema (H2，h2 profile 单机极简模式专用)
--
-- 由 schema-mysql.sql 派生，方言差异：
-- 1. 去掉 ENGINE / CHARSET / ROW_FORMAT 表选项；
-- 2. JSON 列 → CLOB（应用层以字符串读写，语义不变）；
-- 3. 去掉 FULLTEXT ngram 索引（H2 不支持），h2 profile 同时关闭
--    knowledge.search.keyword-enabled，检索退化为纯向量；
-- 4. 去掉索引前缀长度（external_id(159) / slug(189)，H2 无 767B 键长限制）；
-- 5. 其余（AUTO_INCREMENT / ON UPDATE CURRENT_TIMESTAMP / 内联 KEY）H2 MySQL 模式原生支持。

CREATE TABLE IF NOT EXISTS kb_user (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(64)  NOT NULL,
    password    VARCHAR(128) NOT NULL,
    display_name VARCHAR(64) NOT NULL DEFAULT '',
    role        VARCHAR(32)  NOT NULL DEFAULT 'USER',
    status      SMALLINT     NOT NULL DEFAULT 1,
    must_change_password SMALLINT NOT NULL DEFAULT 0,
    auth_source VARCHAR(32)  NOT NULL DEFAULT 'LOCAL',
    external_id VARCHAR(256) NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_username (username),
    UNIQUE KEY uk_user_auth_external (auth_source, external_id)
);

CREATE TABLE IF NOT EXISTS kb_workspace (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    owner_id    BIGINT       NOT NULL,
    settings    CLOB         NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_workspace_owner (owner_id)
);

CREATE TABLE IF NOT EXISTS kb_knowledge_base (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT      NULL,
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(512) DEFAULT '',
    owner_id    BIGINT       NOT NULL,
    visibility  VARCHAR(32)  NOT NULL DEFAULT 'PRIVATE',
    search_alpha DOUBLE      NOT NULL DEFAULT 0.6,
    search_top_k INT          NOT NULL DEFAULT 8,
    status      SMALLINT     NOT NULL DEFAULT 1,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_kb_owner (owner_id),
    KEY idx_kb_workspace (workspace_id)
);

CREATE TABLE IF NOT EXISTS kb_kb_member (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    kb_id       BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    permission  VARCHAR(32)  NOT NULL DEFAULT 'READ',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_member_kb_user (kb_id, user_id),
    KEY idx_member_user (user_id)
);

CREATE TABLE IF NOT EXISTS kb_document (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    kb_id        BIGINT       NOT NULL,
    title        VARCHAR(256) NOT NULL,
    file_name    VARCHAR(256) NOT NULL,
    file_type    VARCHAR(32)  NOT NULL,
    file_size    BIGINT       NOT NULL DEFAULT 0,
    file_path    VARCHAR(512) NOT NULL,
    index_status VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    index_error  VARCHAR(512) NULL,
    chunk_count  INT          NOT NULL DEFAULT 0,
    enabled      SMALLINT     NOT NULL DEFAULT 1,
    created_by   BIGINT       NOT NULL,
    doc_type     VARCHAR(32)  NULL,
    doc_no       VARCHAR(128) NULL,
    effective_date DATE       NULL,
    expire_date  DATE         NULL,
    department   VARCHAR(128) NULL,
    tags         VARCHAR(512) NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_doc_kb (kb_id),
    KEY idx_doc_kb_created (kb_id, created_at),
    KEY idx_doc_status (index_status)
);

CREATE TABLE IF NOT EXISTS kb_document_chunk (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    kb_id       BIGINT       NOT NULL,
    document_id BIGINT       NOT NULL,
    chunk_index INT          NOT NULL,
    content     TEXT         NOT NULL,
    section_title VARCHAR(256) NULL,
    context_prefix VARCHAR(512) NULL,
    page_no        INT          NULL,
    anchor_type    VARCHAR(16)  NULL,
    token_count INT          NOT NULL DEFAULT 0,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_chunk_doc (document_id),
    KEY idx_chunk_kb (kb_id)
);

CREATE TABLE IF NOT EXISTS kb_index_task (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT       NOT NULL,
    status      VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    retry_count INT          NOT NULL DEFAULT 0,
    error_msg   VARCHAR(512) NULL,
    locked_by   VARCHAR(64)  NULL,
    locked_at   DATETIME     NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_task_doc (document_id),
    KEY idx_task_status (status),
    KEY idx_task_updated (updated_at),
    KEY idx_task_status_updated (status, updated_at)
);

CREATE TABLE IF NOT EXISTS kb_chat_session (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    kb_id       BIGINT       NULL,
    title       VARCHAR(256) NOT NULL DEFAULT '新对话',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_session_user (user_id)
);

CREATE TABLE IF NOT EXISTS kb_chat_message (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id  BIGINT       NOT NULL,
    role        VARCHAR(16)  NOT NULL,
    content     TEXT         NOT NULL,
    sources     CLOB         NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_msg_session (session_id)
);

CREATE TABLE IF NOT EXISTS kb_audit_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NULL,
    action      VARCHAR(64)  NOT NULL,
    target_type VARCHAR(64)  NULL,
    target_id   BIGINT       NULL,
    detail      VARCHAR(1024) NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_audit_user (user_id),
    KEY idx_audit_action (action),
    KEY idx_audit_created (created_at)
);

CREATE TABLE IF NOT EXISTS kb_api_key (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    key_prefix  VARCHAR(16)  NOT NULL,
    key_hash    VARCHAR(128) NOT NULL,
    user_id     BIGINT       NOT NULL,
    kb_id       BIGINT       NULL,
    status      SMALLINT     NOT NULL DEFAULT 1,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at DATETIME    NULL,
    KEY idx_api_key_prefix (key_prefix)
);

CREATE TABLE IF NOT EXISTS kb_wiki_page (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    kb_id         BIGINT       NOT NULL,
    slug          VARCHAR(256) NOT NULL,
    title         VARCHAR(256) NOT NULL,
    content_md    TEXT         NOT NULL,
    status        VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    source_doc_ids VARCHAR(512) NULL,
    version       INT          NOT NULL DEFAULT 1,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_wiki_kb_slug (kb_id, slug),
    KEY idx_wiki_page_kb (kb_id)
);

CREATE TABLE IF NOT EXISTS kb_wiki_link (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    kb_id         BIGINT       NOT NULL,
    from_page_id  BIGINT       NOT NULL,
    to_page_id    BIGINT       NOT NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_wiki_link_from_to (from_page_id, to_page_id)
);

CREATE TABLE IF NOT EXISTS kb_wiki_compile_task (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    kb_id         BIGINT       NOT NULL,
    document_id   BIGINT       NOT NULL,
    status        VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    error_msg     VARCHAR(512) NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_wiki_task_doc (document_id),
    KEY idx_wiki_task_status (status)
);

-- Wiki 维护 Agent 变更日志（增量合并 / 全量编译，供审计与回溯）
CREATE TABLE IF NOT EXISTS kb_wiki_change_log (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    kb_id         BIGINT       NOT NULL,
    page_id       BIGINT       NOT NULL,
    from_version  INT          NULL,
    to_version    INT          NOT NULL,
    change_type   VARCHAR(32)  NOT NULL,
    summary       VARCHAR(1000) NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_wiki_change_page (page_id)
);

CREATE TABLE IF NOT EXISTS kb_system_config (
    config_key   VARCHAR(64) PRIMARY KEY,
    config_value VARCHAR(1024) NOT NULL,
    updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- RAG 问答历史（运营抽检 / 内审导出）
CREATE TABLE IF NOT EXISTS kb_qa_history (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT       NULL,
    kb_id        BIGINT       NOT NULL,
    question     TEXT         NOT NULL,
    answer       TEXT         NOT NULL,
    sources      CLOB         NULL,
    source_count INT          NOT NULL DEFAULT 0,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_qa_history_kb (kb_id, created_at),
    KEY idx_qa_history_created (created_at)
);

-- 评测闭环（WP3）：数据集 / 用例 / 运行 / 运行明细 — 检索质量门禁
CREATE TABLE IF NOT EXISTS kb_eval_dataset (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    kb_id       BIGINT       NOT NULL,
    top_k       INT          NOT NULL DEFAULT 8,
    description VARCHAR(512) DEFAULT '',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_eval_dataset_kb (kb_id)
);

CREATE TABLE IF NOT EXISTS kb_eval_case (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_id          BIGINT        NOT NULL,
    question            VARCHAR(1024) NOT NULL,
    expected_chunk_ids  CLOB          NULL,
    expected_keywords   CLOB          NULL,
    enabled             SMALLINT      NOT NULL DEFAULT 1,
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_eval_case_dataset (dataset_id)
);

CREATE TABLE IF NOT EXISTS kb_eval_run (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_id   BIGINT       NOT NULL,
    kb_id        BIGINT       NOT NULL,
    top_k        INT          NOT NULL,
    status       VARCHAR(32)  NOT NULL DEFAULT 'RUNNING',
    total_cases  INT          NOT NULL DEFAULT 0,
    metrics_json CLOB         NULL,
    error        VARCHAR(1024) NULL,
    started_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at  DATETIME     NULL,
    KEY idx_eval_run_dataset (dataset_id)
);

CREATE TABLE IF NOT EXISTS kb_eval_run_item (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id         BIGINT        NOT NULL,
    case_id        BIGINT        NOT NULL,
    question       VARCHAR(1024) NOT NULL,
    first_hit_rank INT           NULL,
    recall         DOUBLE        NULL,
    keyword_hit    SMALLINT      NULL,
    latency_ms     INT           NOT NULL DEFAULT 0,
    hit_chunk_ids  CLOB          NULL,
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_eval_run_item_run (run_id)
);

-- WP7 知识图谱（GraphRAG 轻量版）：邻接表实现，实体 KB 级隔离，查询限 1-2 跳
CREATE TABLE IF NOT EXISTS kg_entity (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    kb_id       BIGINT       NOT NULL,
    name        VARCHAR(128) NOT NULL,
    type        VARCHAR(32)  NOT NULL DEFAULT '其他',
    description VARCHAR(512) DEFAULT '',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_kg_entity (kb_id, name),
    KEY idx_kg_entity_kb (kb_id)
);

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
);

-- WP9 主动知识运营：无结果/低分检索查询缺口采集（运营聚类→补文档建议）
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
);

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
);
