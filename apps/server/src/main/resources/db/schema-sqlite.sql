-- Fast Knowledge baseline schema (SQLite standalone)

CREATE TABLE IF NOT EXISTS kb_user (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    username    TEXT NOT NULL UNIQUE,
    password    TEXT NOT NULL,
    display_name TEXT NOT NULL DEFAULT '',
    role        TEXT NOT NULL DEFAULT 'USER',
    status      INTEGER NOT NULL DEFAULT 1,
    must_change_password INTEGER NOT NULL DEFAULT 0,
    created_at  TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at  TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS kb_workspace (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT NOT NULL,
    owner_id    INTEGER NOT NULL,
    settings    TEXT,
    created_at  TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at  TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_workspace_owner ON kb_workspace (owner_id);

CREATE TABLE IF NOT EXISTS kb_knowledge_base (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    workspace_id INTEGER,
    name        TEXT NOT NULL,
    description TEXT DEFAULT '',
    owner_id    INTEGER NOT NULL,
    visibility  TEXT NOT NULL DEFAULT 'PRIVATE',
    search_alpha REAL NOT NULL DEFAULT 0.6,
    search_top_k INTEGER NOT NULL DEFAULT 8,
    status      INTEGER NOT NULL DEFAULT 1,
    created_at  TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at  TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_kb_owner ON kb_knowledge_base (owner_id);
CREATE INDEX IF NOT EXISTS idx_kb_workspace ON kb_knowledge_base (workspace_id);

CREATE TABLE IF NOT EXISTS kb_kb_member (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    kb_id       INTEGER NOT NULL,
    user_id     INTEGER NOT NULL,
    permission  TEXT NOT NULL DEFAULT 'READ',
    created_at  TEXT NOT NULL DEFAULT (datetime('now')),
    UNIQUE (kb_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_member_user ON kb_kb_member (user_id);

CREATE TABLE IF NOT EXISTS kb_document (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    kb_id        INTEGER NOT NULL,
    title        TEXT NOT NULL,
    file_name    TEXT NOT NULL,
    file_type    TEXT NOT NULL,
    file_size    INTEGER NOT NULL DEFAULT 0,
    file_path    TEXT NOT NULL,
    index_status TEXT NOT NULL DEFAULT 'PENDING',
    index_error  TEXT,
    chunk_count  INTEGER NOT NULL DEFAULT 0,
    enabled      INTEGER NOT NULL DEFAULT 1,
    created_by   INTEGER NOT NULL,
    created_at   TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at   TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_doc_kb ON kb_document (kb_id);
CREATE INDEX IF NOT EXISTS idx_doc_status ON kb_document (index_status);

CREATE TABLE IF NOT EXISTS kb_document_chunk (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    kb_id       INTEGER NOT NULL,
    document_id INTEGER NOT NULL,
    chunk_index INTEGER NOT NULL,
    content     TEXT NOT NULL,
    token_count INTEGER NOT NULL DEFAULT 0,
    created_at  TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_chunk_doc ON kb_document_chunk (document_id);
CREATE INDEX IF NOT EXISTS idx_chunk_kb ON kb_document_chunk (kb_id);

CREATE TABLE IF NOT EXISTS kb_index_task (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    document_id INTEGER NOT NULL,
    status      TEXT NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER NOT NULL DEFAULT 0,
    error_msg   TEXT,
    locked_by   TEXT,
    locked_at   TEXT,
    created_at  TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at  TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_task_doc ON kb_index_task (document_id);
CREATE INDEX IF NOT EXISTS idx_task_status ON kb_index_task (status);

CREATE TABLE IF NOT EXISTS kb_chat_session (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id     INTEGER NOT NULL,
    kb_id       INTEGER,
    title       TEXT NOT NULL DEFAULT '新对话',
    created_at  TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at  TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_session_user ON kb_chat_session (user_id);

CREATE TABLE IF NOT EXISTS kb_chat_message (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id  INTEGER NOT NULL,
    role        TEXT NOT NULL,
    content     TEXT NOT NULL,
    sources     TEXT,
    created_at  TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_msg_session ON kb_chat_message (session_id);

CREATE TABLE IF NOT EXISTS kb_audit_log (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id     INTEGER,
    action      TEXT NOT NULL,
    target_type TEXT,
    target_id   INTEGER,
    detail      TEXT,
    created_at  TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_audit_user ON kb_audit_log (user_id);

CREATE TABLE IF NOT EXISTS kb_system_config (
    config_key   TEXT PRIMARY KEY,
    config_value TEXT NOT NULL,
    updated_at   TEXT NOT NULL DEFAULT (datetime('now'))
);

-- 向量表（embedding 为 JSON 浮点数组，供 sqlite-vec 函数或 Java 回退扫描）
CREATE TABLE IF NOT EXISTS kb_vector_chunk (
    chunk_id    INTEGER PRIMARY KEY,
    kb_id       INTEGER NOT NULL,
    doc_id      INTEGER NOT NULL,
    title       TEXT,
    content     TEXT NOT NULL,
    embedding   TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_vector_chunk_kb ON kb_vector_chunk (kb_id);
CREATE INDEX IF NOT EXISTS idx_vector_chunk_doc ON kb_vector_chunk (doc_id);

CREATE VIRTUAL TABLE IF NOT EXISTS kb_vector_chunk_fts USING fts5(
    title,
    content,
    chunk_id UNINDEXED,
    kb_id UNINDEXED,
    doc_id UNINDEXED,
    tokenize = 'unicode61'
);
