-- 向量表定义已合并至 classpath:db/schema-postgres.sql
-- 本文件仅作 Docker 卷挂载参考

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS kb_vector_chunk (
    chunk_id    BIGINT PRIMARY KEY,
    kb_id       BIGINT NOT NULL,
    doc_id      BIGINT NOT NULL,
    title       VARCHAR(256),
    content     TEXT NOT NULL,
    embedding   vector(512) NOT NULL,
    search_text tsvector
);

CREATE INDEX IF NOT EXISTS idx_vector_kb ON kb_vector_chunk (kb_id);
CREATE INDEX IF NOT EXISTS idx_vector_embedding ON kb_vector_chunk USING hnsw (embedding vector_cosine_ops);
CREATE INDEX IF NOT EXISTS idx_vector_fts ON kb_vector_chunk USING gin (search_text);
