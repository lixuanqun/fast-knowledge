-- PostgreSQL + pgvector 初始化（VECTOR_PROVIDER=pgvector 时使用）
-- CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS kb_vector_chunk (
    chunk_id   BIGINT PRIMARY KEY,
    kb_id      BIGINT NOT NULL,
    doc_id     BIGINT NOT NULL,
    title      VARCHAR(256),
    content    TEXT NOT NULL,
    embedding  vector(512)
);

CREATE INDEX IF NOT EXISTS idx_vector_kb ON kb_vector_chunk (kb_id);
CREATE INDEX IF NOT EXISTS idx_vector_embedding ON kb_vector_chunk USING hnsw (embedding vector_cosine_ops);
