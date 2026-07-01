-- Fast Knowledge baseline schema

CREATE TABLE IF NOT EXISTS kb_user (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    username    VARCHAR(64)  NOT NULL UNIQUE,
    password    VARCHAR(128) NOT NULL,
    display_name VARCHAR(64) NOT NULL DEFAULT '',
    role        VARCHAR(32)  NOT NULL DEFAULT 'USER' COMMENT 'ADMIN/USER',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1=active 0=disabled',
    must_change_password TINYINT NOT NULL DEFAULT 0,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_workspace (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    name        VARCHAR(128) NOT NULL,
    owner_id    BIGINT       NOT NULL,
    settings    JSON         DEFAULT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_workspace_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_knowledge_base (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    workspace_id BIGINT      DEFAULT NULL,
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(512) DEFAULT '',
    owner_id    BIGINT       NOT NULL,
    visibility  VARCHAR(32)  NOT NULL DEFAULT 'PRIVATE' COMMENT 'PRIVATE/PUBLIC',
    search_alpha DOUBLE      NOT NULL DEFAULT 0.6,
    search_top_k INT          NOT NULL DEFAULT 8,
    status      TINYINT      NOT NULL DEFAULT 1,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_kb_owner (owner_id),
    INDEX idx_kb_workspace (workspace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_kb_member (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    kb_id       BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    permission  VARCHAR(32)  NOT NULL DEFAULT 'READ' COMMENT 'READ/WRITE/ADMIN',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_kb_user (kb_id, user_id),
    INDEX idx_member_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_document (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    kb_id        BIGINT       NOT NULL,
    title        VARCHAR(256) NOT NULL,
    file_name    VARCHAR(256) NOT NULL,
    file_type    VARCHAR(32)  NOT NULL,
    file_size    BIGINT       NOT NULL DEFAULT 0,
    file_path    VARCHAR(512) NOT NULL,
    index_status VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/INDEXING/INDEXED/FAILED',
    index_error  VARCHAR(512) DEFAULT NULL,
    chunk_count  INT          NOT NULL DEFAULT 0,
    enabled      TINYINT      NOT NULL DEFAULT 1,
    created_by   BIGINT       NOT NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_doc_kb (kb_id),
    INDEX idx_doc_status (index_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_document_chunk (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    kb_id       BIGINT       NOT NULL,
    document_id BIGINT       NOT NULL,
    chunk_index INT          NOT NULL,
    content     TEXT         NOT NULL,
    token_count INT          NOT NULL DEFAULT 0,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_chunk_doc (document_id),
    INDEX idx_chunk_kb (kb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_index_task (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT       NOT NULL,
    status      VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    retry_count INT          NOT NULL DEFAULT 0,
    error_msg   VARCHAR(512) DEFAULT NULL,
    locked_by   VARCHAR(64)  DEFAULT NULL,
    locked_at   DATETIME     DEFAULT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_task_doc (document_id),
    INDEX idx_task_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_chat_session (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    kb_id       BIGINT       DEFAULT NULL,
    title       VARCHAR(256) NOT NULL DEFAULT '新对话',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_session_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_chat_message (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id  BIGINT       NOT NULL,
    role        VARCHAR(16)  NOT NULL COMMENT 'user/assistant/system',
    content     TEXT         NOT NULL,
    sources     JSON         DEFAULT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_msg_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_audit_log (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT       DEFAULT NULL,
    action      VARCHAR(64)  NOT NULL,
    target_type VARCHAR(64)  DEFAULT NULL,
    target_id   BIGINT       DEFAULT NULL,
    detail      VARCHAR(1024) DEFAULT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_system_config (
    config_key   VARCHAR(64) PRIMARY KEY,
    config_value VARCHAR(1024) NOT NULL,
    updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
