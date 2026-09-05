-- Fast Knowledge baseline schema (MySQL 5.7+, Classic 部署形态)
--
-- 说明：
-- 1. Classic 形态使用 knowledge.vector.provider=local（本地文件向量索引），
--    不需要 pgvector / kb_embeddings 表；
-- 2. kb_document_chunk.content 与 kb_document.title 建 FULLTEXT ngram 中文全文索引，
--    供混合检索的全文支路使用（本地向量检索默认仅走向量支路）；
-- 3. MySQL 不支持 CREATE INDEX IF NOT EXISTS / 部分索引，索引全部内联在建表语句中，
--    幂等性依赖 CREATE TABLE IF NOT EXISTS。

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS kb_workspace (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    owner_id    BIGINT       NOT NULL,
    settings    JSON         NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_workspace_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS kb_kb_member (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    kb_id       BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    permission  VARCHAR(32)  NOT NULL DEFAULT 'READ',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_member_kb_user (kb_id, user_id),
    KEY idx_member_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;

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
    KEY idx_doc_status (index_status),
    FULLTEXT KEY ft_doc_title (title) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS kb_document_chunk (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    kb_id       BIGINT       NOT NULL,
    document_id BIGINT       NOT NULL,
    chunk_index INT          NOT NULL,
    content     TEXT         NOT NULL,
    section_title VARCHAR(256) NULL,
    token_count INT          NOT NULL DEFAULT 0,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_chunk_doc (document_id),
    KEY idx_chunk_kb (kb_id),
    FULLTEXT KEY ft_chunk_content (content) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS kb_chat_session (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    kb_id       BIGINT       NULL,
    title       VARCHAR(256) NOT NULL DEFAULT '新对话',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_session_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS kb_chat_message (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id  BIGINT       NOT NULL,
    role        VARCHAR(16)  NOT NULL,
    content     TEXT         NOT NULL,
    sources     JSON         NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_msg_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS kb_wiki_link (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    kb_id         BIGINT       NOT NULL,
    from_page_id  BIGINT       NOT NULL,
    to_page_id    BIGINT       NOT NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_wiki_link_from_to (from_page_id, to_page_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS kb_system_config (
    config_key   VARCHAR(64) PRIMARY KEY,
    config_value VARCHAR(1024) NOT NULL,
    updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;

-- RAG 问答历史（运营抽检 / 内审导出）
CREATE TABLE IF NOT EXISTS kb_qa_history (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT       NULL,
    kb_id        BIGINT       NOT NULL,
    question     TEXT         NOT NULL,
    answer       TEXT         NOT NULL,
    sources      JSON         NULL,
    source_count INT          NOT NULL DEFAULT 0,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_qa_history_kb (kb_id, created_at),
    KEY idx_qa_history_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;

-- 向量存储：Classic 形态由 LocalEmbeddingStore 持久化为 data/vectors/kb-{id}.json（不入库）
