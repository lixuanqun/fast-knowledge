CREATE TABLE IF NOT EXISTS kb_user (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    username    VARCHAR(64) NOT NULL UNIQUE,
    password    VARCHAR(128) NOT NULL,
    display_name VARCHAR(64) NOT NULL DEFAULT '',
    role        VARCHAR(32) NOT NULL DEFAULT 'USER',
    status      TINYINT NOT NULL DEFAULT 1,
    must_change_password TINYINT NOT NULL DEFAULT 0,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS kb_workspace (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    name        VARCHAR(128) NOT NULL,
    owner_id    BIGINT NOT NULL,
    settings    VARCHAR(4096) DEFAULT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_workspace_owner ON kb_workspace (owner_id);

CREATE TABLE IF NOT EXISTS kb_knowledge_base (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    workspace_id BIGINT DEFAULT NULL,
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(512) DEFAULT '',
    owner_id    BIGINT NOT NULL,
    visibility  VARCHAR(32) NOT NULL DEFAULT 'PRIVATE',
    search_alpha DOUBLE NOT NULL DEFAULT 0.6,
    search_top_k INT NOT NULL DEFAULT 8,
    status      TINYINT NOT NULL DEFAULT 1,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_kb_owner ON kb_knowledge_base (owner_id);
CREATE INDEX IF NOT EXISTS idx_kb_workspace ON kb_knowledge_base (workspace_id);

CREATE TABLE IF NOT EXISTS kb_kb_member (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    kb_id       BIGINT NOT NULL,
    user_id     BIGINT NOT NULL,
    permission  VARCHAR(32) NOT NULL DEFAULT 'READ',
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_kb_user UNIQUE (kb_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_member_user ON kb_kb_member (user_id);

CREATE TABLE IF NOT EXISTS kb_document (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    kb_id        BIGINT NOT NULL,
    title        VARCHAR(256) NOT NULL,
    file_name    VARCHAR(256) NOT NULL,
    file_type    VARCHAR(32) NOT NULL,
    file_size    BIGINT NOT NULL DEFAULT 0,
    file_path    VARCHAR(512) NOT NULL,
    index_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    index_error  VARCHAR(512) DEFAULT NULL,
    chunk_count  INT NOT NULL DEFAULT 0,
    enabled      TINYINT NOT NULL DEFAULT 1,
    created_by   BIGINT NOT NULL,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_doc_kb ON kb_document (kb_id);
CREATE INDEX IF NOT EXISTS idx_doc_status ON kb_document (index_status);

CREATE TABLE IF NOT EXISTS kb_document_chunk (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    kb_id       BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    chunk_index INT NOT NULL,
    content     CLOB NOT NULL,
    token_count INT NOT NULL DEFAULT 0,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chunk_doc ON kb_document_chunk (document_id);
CREATE INDEX IF NOT EXISTS idx_chunk_kb ON kb_document_chunk (kb_id);

CREATE TABLE IF NOT EXISTS kb_index_task (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    status      VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    error_msg   VARCHAR(512) DEFAULT NULL,
    locked_by   VARCHAR(64) DEFAULT NULL,
    locked_at   TIMESTAMP DEFAULT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_task_doc ON kb_index_task (document_id);
CREATE INDEX IF NOT EXISTS idx_task_status ON kb_index_task (status);

CREATE TABLE IF NOT EXISTS kb_chat_session (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT NOT NULL,
    kb_id       BIGINT DEFAULT NULL,
    title       VARCHAR(256) NOT NULL DEFAULT '新对话',
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_session_user ON kb_chat_session (user_id);

CREATE TABLE IF NOT EXISTS kb_chat_message (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id  BIGINT NOT NULL,
    role        VARCHAR(16) NOT NULL,
    content     CLOB NOT NULL,
    sources     VARCHAR(4096) DEFAULT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_msg_session ON kb_chat_message (session_id);

CREATE TABLE IF NOT EXISTS kb_audit_log (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT DEFAULT NULL,
    action      VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) DEFAULT NULL,
    target_id   BIGINT DEFAULT NULL,
    detail      VARCHAR(1024) DEFAULT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_user ON kb_audit_log (user_id);

CREATE TABLE IF NOT EXISTS kb_system_config (
    config_key   VARCHAR(64) PRIMARY KEY,
    config_value VARCHAR(1024) NOT NULL,
    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
