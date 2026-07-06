-- Fast Knowledge baseline schema (PostgreSQL + pgvector cluster)

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS kb_user (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(64)  NOT NULL UNIQUE,
    password    VARCHAR(128) NOT NULL,
    display_name VARCHAR(64) NOT NULL DEFAULT '',
    role        VARCHAR(32)  NOT NULL DEFAULT 'USER',
    status      SMALLINT     NOT NULL DEFAULT 1,
    must_change_password SMALLINT NOT NULL DEFAULT 0,
    created_at  TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS kb_workspace (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    owner_id    BIGINT       NOT NULL,
    settings    JSONB,
    created_at  TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_workspace_owner ON kb_workspace (owner_id);

CREATE TABLE IF NOT EXISTS kb_knowledge_base (
    id          BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT,
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(512) DEFAULT '',
    owner_id    BIGINT       NOT NULL,
    visibility  VARCHAR(32)  NOT NULL DEFAULT 'PRIVATE',
    search_alpha DOUBLE PRECISION NOT NULL DEFAULT 0.6,
    search_top_k INT          NOT NULL DEFAULT 8,
    status      SMALLINT     NOT NULL DEFAULT 1,
    created_at  TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_kb_owner ON kb_knowledge_base (owner_id);
CREATE INDEX IF NOT EXISTS idx_kb_workspace ON kb_knowledge_base (workspace_id);

CREATE TABLE IF NOT EXISTS kb_kb_member (
    id          BIGSERIAL PRIMARY KEY,
    kb_id       BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    permission  VARCHAR(32)  NOT NULL DEFAULT 'READ',
    created_at  TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (kb_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_member_user ON kb_kb_member (user_id);

CREATE TABLE IF NOT EXISTS kb_document (
    id           BIGSERIAL PRIMARY KEY,
    kb_id        BIGINT       NOT NULL,
    title        VARCHAR(256) NOT NULL,
    file_name    VARCHAR(256) NOT NULL,
    file_type    VARCHAR(32)  NOT NULL,
    file_size    BIGINT       NOT NULL DEFAULT 0,
    file_path    VARCHAR(512) NOT NULL,
    index_status VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    index_error  VARCHAR(512),
    chunk_count  INT          NOT NULL DEFAULT 0,
    enabled      SMALLINT     NOT NULL DEFAULT 1,
    created_by   BIGINT       NOT NULL,
    doc_type     VARCHAR(32),
    doc_no       VARCHAR(128),
    effective_date DATE,
    expire_date  DATE,
    department   VARCHAR(128),
    tags         VARCHAR(512),
    created_at   TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_doc_kb ON kb_document (kb_id);
CREATE INDEX IF NOT EXISTS idx_doc_kb_created ON kb_document (kb_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_doc_status ON kb_document (index_status);

CREATE TABLE IF NOT EXISTS kb_document_chunk (
    id          BIGSERIAL PRIMARY KEY,
    kb_id       BIGINT       NOT NULL,
    document_id BIGINT       NOT NULL,
    chunk_index INT          NOT NULL,
    content     TEXT         NOT NULL,
    section_title VARCHAR(256),
    token_count INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_chunk_doc ON kb_document_chunk (document_id);
CREATE INDEX IF NOT EXISTS idx_chunk_kb ON kb_document_chunk (kb_id);

CREATE TABLE IF NOT EXISTS kb_index_task (
    id          BIGSERIAL PRIMARY KEY,
    document_id BIGINT       NOT NULL,
    status      VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    retry_count INT          NOT NULL DEFAULT 0,
    error_msg   VARCHAR(512),
    locked_by   VARCHAR(64),
    locked_at   TIMESTAMP,
    created_at  TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_task_doc ON kb_index_task (document_id);
CREATE INDEX IF NOT EXISTS idx_task_status ON kb_index_task (status);
CREATE INDEX IF NOT EXISTS idx_task_updated ON kb_index_task (updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_task_status_updated ON kb_index_task (status, updated_at DESC);

CREATE TABLE IF NOT EXISTS kb_chat_session (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    kb_id       BIGINT,
    title       VARCHAR(256) NOT NULL DEFAULT '新对话',
    created_at  TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_session_user ON kb_chat_session (user_id);

CREATE TABLE IF NOT EXISTS kb_chat_message (
    id          BIGSERIAL PRIMARY KEY,
    session_id  BIGINT       NOT NULL,
    role        VARCHAR(16)  NOT NULL,
    content     TEXT         NOT NULL,
    sources     JSONB,
    created_at  TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_msg_session ON kb_chat_message (session_id);

CREATE TABLE IF NOT EXISTS kb_audit_log (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT,
    action      VARCHAR(64)  NOT NULL,
    target_type VARCHAR(64),
    target_id   BIGINT,
    detail      VARCHAR(1024),
    created_at  TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_audit_user ON kb_audit_log (user_id);
CREATE INDEX IF NOT EXISTS idx_audit_action ON kb_audit_log (action);
CREATE INDEX IF NOT EXISTS idx_audit_created ON kb_audit_log (created_at);

ALTER TABLE kb_user ADD COLUMN IF NOT EXISTS auth_source VARCHAR(32) NOT NULL DEFAULT 'LOCAL';
ALTER TABLE kb_user ADD COLUMN IF NOT EXISTS external_id VARCHAR(256);
CREATE UNIQUE INDEX IF NOT EXISTS idx_user_auth_external
    ON kb_user (auth_source, external_id) WHERE external_id IS NOT NULL;

ALTER TABLE kb_document ADD COLUMN IF NOT EXISTS doc_type VARCHAR(32);
ALTER TABLE kb_document ADD COLUMN IF NOT EXISTS doc_no VARCHAR(128);
ALTER TABLE kb_document ADD COLUMN IF NOT EXISTS effective_date DATE;
ALTER TABLE kb_document ADD COLUMN IF NOT EXISTS expire_date DATE;
ALTER TABLE kb_document ADD COLUMN IF NOT EXISTS department VARCHAR(128);
ALTER TABLE kb_document ADD COLUMN IF NOT EXISTS tags VARCHAR(512);
ALTER TABLE kb_document_chunk ADD COLUMN IF NOT EXISTS section_title VARCHAR(256);

CREATE TABLE IF NOT EXISTS kb_api_key (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    key_prefix  VARCHAR(16)  NOT NULL,
    key_hash    VARCHAR(128) NOT NULL,
    user_id     BIGINT       NOT NULL,
    kb_id       BIGINT,
    status      SMALLINT     NOT NULL DEFAULT 1,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_api_key_prefix ON kb_api_key (key_prefix);

CREATE TABLE IF NOT EXISTS kb_wiki_page (
    id            BIGSERIAL PRIMARY KEY,
    kb_id         BIGINT       NOT NULL,
    slug          VARCHAR(256) NOT NULL,
    title         VARCHAR(256) NOT NULL,
    content_md    TEXT         NOT NULL,
    status        VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    source_doc_ids VARCHAR(512),
    version       INT          NOT NULL DEFAULT 1,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (kb_id, slug)
);
CREATE INDEX IF NOT EXISTS idx_wiki_page_kb ON kb_wiki_page (kb_id);

CREATE TABLE IF NOT EXISTS kb_wiki_link (
    id            BIGSERIAL PRIMARY KEY,
    kb_id         BIGINT       NOT NULL,
    from_page_id  BIGINT       NOT NULL,
    to_page_id    BIGINT       NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (from_page_id, to_page_id)
);

CREATE TABLE IF NOT EXISTS kb_wiki_compile_task (
    id            BIGSERIAL PRIMARY KEY,
    kb_id         BIGINT       NOT NULL,
    document_id   BIGINT       NOT NULL,
    status        VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    error_msg     VARCHAR(512),
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_wiki_task_doc ON kb_wiki_compile_task (document_id);
CREATE INDEX IF NOT EXISTS idx_wiki_task_status ON kb_wiki_compile_task (status);

CREATE TABLE IF NOT EXISTS kb_system_config (
    config_key   VARCHAR(64) PRIMARY KEY,
    config_value VARCHAR(1024) NOT NULL,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 向量索引由 LangChain4j PgVectorEmbeddingStore 自动建表（默认 kb_embeddings）
