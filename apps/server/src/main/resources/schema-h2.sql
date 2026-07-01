-- H2-compatible schema (MySQL mode) for test & minimal profiles

CREATE TABLE IF NOT EXISTS kb_user (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    username    VARCHAR(64) NOT NULL UNIQUE,
    password    VARCHAR(128) NOT NULL,
    display_name VARCHAR(64) NOT NULL DEFAULT '',
    role        VARCHAR(32) NOT NULL DEFAULT 'USER',
    status      SMALLINT NOT NULL DEFAULT 1,
    must_change_password SMALLINT NOT NULL DEFAULT 0,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS kb_workspace (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    name        VARCHAR(128) NOT NULL,
    owner_id    BIGINT NOT NULL,
    settings    VARCHAR(4096),
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS kb_knowledge_base (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    workspace_id BIGINT,
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(512) DEFAULT '',
    owner_id    BIGINT NOT NULL,
    visibility  VARCHAR(32) NOT NULL DEFAULT 'PRIVATE',
    search_alpha DOUBLE NOT NULL DEFAULT 0.6,
    search_top_k INT NOT NULL DEFAULT 8,
    status      SMALLINT NOT NULL DEFAULT 1,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS kb_kb_member (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    kb_id       BIGINT NOT NULL,
    user_id     BIGINT NOT NULL,
    permission  VARCHAR(32) NOT NULL DEFAULT 'READ',
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_kb_user UNIQUE (kb_id, user_id)
);

CREATE TABLE IF NOT EXISTS kb_document (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    kb_id        BIGINT NOT NULL,
    title        VARCHAR(256) NOT NULL,
    file_name    VARCHAR(256) NOT NULL,
    file_type    VARCHAR(32) NOT NULL,
    file_size    BIGINT NOT NULL DEFAULT 0,
    file_path    VARCHAR(512) NOT NULL,
    index_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    index_error  VARCHAR(512),
    chunk_count  INT NOT NULL DEFAULT 0,
    enabled      SMALLINT NOT NULL DEFAULT 1,
    created_by   BIGINT NOT NULL,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS kb_document_chunk (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    kb_id       BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    chunk_index INT NOT NULL,
    content     CLOB NOT NULL,
    token_count INT NOT NULL DEFAULT 0,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS kb_index_task (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    status      VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    error_msg   VARCHAR(512),
    locked_by   VARCHAR(64),
    locked_at   TIMESTAMP,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS kb_chat_session (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT NOT NULL,
    kb_id       BIGINT,
    title       VARCHAR(256) NOT NULL DEFAULT '新对话',
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS kb_chat_message (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id  BIGINT NOT NULL,
    role        VARCHAR(16) NOT NULL,
    content     CLOB NOT NULL,
    sources     VARCHAR(4096),
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS kb_audit_log (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT,
    action      VARCHAR(64) NOT NULL,
    target_type VARCHAR(64),
    target_id   BIGINT,
    detail      VARCHAR(1024),
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS kb_system_config (
    config_key   VARCHAR(64) PRIMARY KEY,
    config_value VARCHAR(1024) NOT NULL,
    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
