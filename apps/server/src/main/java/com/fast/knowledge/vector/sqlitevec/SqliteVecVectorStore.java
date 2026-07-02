package com.fast.knowledge.vector.sqlitevec;

import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.vector.HybridSearchMerger;
import com.fast.knowledge.vector.SearchHit;
import com.fast.knowledge.vector.VectorChunk;
import com.fast.knowledge.vector.VectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "knowledge.vector.provider", havingValue = "sqlite-vec")
public class SqliteVecVectorStore implements VectorStore {

    private final DataSource dataSource;
    private final KnowledgeProperties properties;
    private volatile boolean extensionLoaded;

    public SqliteVecVectorStore(DataSource dataSource, KnowledgeProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
        initExtension();
        log.info("SQLite + sqlite-vec 向量存储已启用，扩展加载={}", extensionLoaded);
    }

    private void initExtension() {
        try (Connection conn = dataSource.getConnection()) {
            extensionLoaded = SqliteVecSupport.tryLoadExtension(
                    conn, properties.getVector().getSqliteVec().getExtensionPath());
        } catch (SQLException e) {
            log.warn("初始化 sqlite-vec 失败: {}", e.getMessage());
            extensionLoaded = false;
        }
    }

    @Override
    public void addChunks(Long kbId, List<VectorChunk> chunks) throws IOException {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        String upsertSql = """
                INSERT INTO kb_vector_chunk (chunk_id, kb_id, doc_id, title, content, embedding)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(chunk_id) DO UPDATE SET
                    kb_id = excluded.kb_id,
                    doc_id = excluded.doc_id,
                    title = excluded.title,
                    content = excluded.content,
                    embedding = excluded.embedding
                """;
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
                for (VectorChunk chunk : chunks) {
                    ps.setLong(1, chunk.getChunkId());
                    ps.setLong(2, kbId);
                    ps.setLong(3, chunk.getDocId());
                    ps.setString(4, chunk.getTitle());
                    ps.setString(5, chunk.getContent());
                    ps.setString(6, SqliteVecSupport.toVecJson(chunk.getVector()));
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            syncFts(conn, chunks, kbId);
            conn.commit();
        } catch (SQLException e) {
            throw new IOException("SQLite 向量写入失败", e);
        }
    }

    private void syncFts(Connection conn, List<VectorChunk> chunks, Long kbId) throws SQLException {
        String existsSql = "SELECT rowid, title, content, chunk_id, kb_id, doc_id FROM kb_vector_chunk_fts WHERE chunk_id = ? LIMIT 1";
        String deleteSql = """
                INSERT INTO kb_vector_chunk_fts(kb_vector_chunk_fts, title, content, chunk_id, kb_id, doc_id)
                VALUES ('delete', ?, ?, ?, ?, ?)
                """;
        String insertSql = """
                INSERT INTO kb_vector_chunk_fts(chunk_id, kb_id, doc_id, title, content)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement existsPs = conn.prepareStatement(existsSql);
             PreparedStatement deletePs = conn.prepareStatement(deleteSql);
             PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
            for (VectorChunk chunk : chunks) {
                existsPs.setLong(1, chunk.getChunkId());
                try (ResultSet rs = existsPs.executeQuery()) {
                    if (rs.next()) {
                        deletePs.setString(1, rs.getString("title"));
                        deletePs.setString(2, rs.getString("content"));
                        deletePs.setLong(3, rs.getLong("chunk_id"));
                        deletePs.setLong(4, rs.getLong("kb_id"));
                        deletePs.setLong(5, rs.getLong("doc_id"));
                        deletePs.executeUpdate();
                    }
                }

                insertPs.setLong(1, chunk.getChunkId());
                insertPs.setLong(2, kbId);
                insertPs.setLong(3, chunk.getDocId());
                insertPs.setString(4, chunk.getTitle() != null ? chunk.getTitle() : "");
                insertPs.setString(5, chunk.getContent());
                insertPs.addBatch();
            }
            insertPs.executeBatch();
        }
    }

    @Override
    public void deleteByDocument(Long kbId, Long docId) throws IOException {
        deleteFtsByFilter("kb_id = ? AND doc_id = ?", kbId, docId);
        executeUpdate("DELETE FROM kb_vector_chunk WHERE kb_id = ? AND doc_id = ?", kbId, docId);
    }

    @Override
    public void deleteChunk(Long kbId, Long chunkId) throws IOException {
        deleteFtsByFilter("chunk_id = ?", chunkId);
        executeUpdate("DELETE FROM kb_vector_chunk WHERE kb_id = ? AND chunk_id = ?", kbId, chunkId);
    }

    @Override
    public void deleteKb(Long kbId) throws IOException {
        deleteFtsByFilter("kb_id = ?", kbId);
        executeUpdate("DELETE FROM kb_vector_chunk WHERE kb_id = ?", kbId);
    }

    @Override
    public List<SearchHit> hybridSearch(Long kbId, String queryText, float[] queryVector,
                                        int topK, double alpha) throws IOException {
        int candidateK = Math.max(topK * 2, topK);
        List<SearchHit> vectorHits = extensionLoaded
                ? vectorSearchWithExtension(kbId, queryVector, candidateK)
                : vectorSearchJavaFallback(kbId, queryVector, candidateK);
        List<SearchHit> textHits = textSearch(kbId, queryText, candidateK);
        return HybridSearchMerger.merge(vectorHits, textHits, alpha, topK);
    }

    private List<SearchHit> vectorSearchWithExtension(Long kbId, float[] queryVector, int limit) throws IOException {
        String vecJson = SqliteVecSupport.toVecJson(queryVector);
        String sql = """
                SELECT chunk_id, doc_id, title, content,
                       1 - vec_distance_cosine(embedding, ?) AS score
                FROM kb_vector_chunk
                WHERE kb_id = ?
                ORDER BY vec_distance_cosine(embedding, ?)
                LIMIT ?
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            SqliteVecSupport.tryLoadExtension(conn, properties.getVector().getSqliteVec().getExtensionPath());
            ps.setString(1, vecJson);
            ps.setLong(2, kbId);
            ps.setString(3, vecJson);
            ps.setInt(4, limit);
            return readHits(kbId, ps);
        } catch (SQLException e) {
            throw new IOException("SQLite 向量检索失败", e);
        }
    }

    private List<SearchHit> vectorSearchJavaFallback(Long kbId, float[] queryVector, int limit) throws IOException {
        String sql = """
                SELECT chunk_id, doc_id, title, content, embedding
                FROM kb_vector_chunk
                WHERE kb_id = ?
                """;
        List<SearchHit> hits = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, kbId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    float[] stored = SqliteVecSupport.parseVecJson(rs.getString("embedding"));
                    double score = SqliteVecSupport.cosineSimilarity(queryVector, stored);
                    SearchHit hit = new SearchHit();
                    hit.setKbId(kbId);
                    hit.setChunkId(rs.getLong("chunk_id"));
                    hit.setDocumentId(rs.getLong("doc_id"));
                    hit.setTitle(rs.getString("title"));
                    hit.setContent(rs.getString("content"));
                    hit.setScore(score);
                    hits.add(hit);
                }
            }
        } catch (SQLException e) {
            throw new IOException("SQLite 向量回退检索失败", e);
        }
        hits.sort(Comparator.comparingDouble(SearchHit::getScore).reversed());
        if (hits.size() > limit) {
            return new ArrayList<>(hits.subList(0, limit));
        }
        return hits;
    }

    private List<SearchHit> textSearch(Long kbId, String queryText, int limit) throws IOException {
        if (queryText == null || queryText.isBlank()) {
            return List.of();
        }
        String sql = """
                SELECT chunk_id, doc_id, title, content,
                       -bm25(kb_vector_chunk_fts) AS score
                FROM kb_vector_chunk_fts
                WHERE kb_vector_chunk_fts MATCH ?
                  AND kb_id = ?
                ORDER BY score DESC
                LIMIT ?
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sanitizeFtsQuery(queryText));
            ps.setLong(2, kbId);
            ps.setInt(3, limit);
            return readHits(kbId, ps);
        } catch (SQLException e) {
            return List.of();
        }
    }

    private List<SearchHit> readHits(Long kbId, PreparedStatement ps) throws SQLException {
        List<SearchHit> hits = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                SearchHit hit = new SearchHit();
                hit.setKbId(kbId);
                hit.setChunkId(rs.getLong("chunk_id"));
                hit.setDocumentId(rs.getLong("doc_id"));
                hit.setTitle(rs.getString("title"));
                hit.setContent(rs.getString("content"));
                hit.setScore(Math.max(rs.getDouble("score"), 0));
                hits.add(hit);
            }
        }
        return hits;
    }

    private void deleteFtsByFilter(String whereClause, Object... params) throws IOException {
        String selectSql = "SELECT rowid, chunk_id, kb_id, doc_id, title, content FROM kb_vector_chunk_fts WHERE " + whereClause;
        String deleteSql = """
                INSERT INTO kb_vector_chunk_fts(kb_vector_chunk_fts, title, content, chunk_id, kb_id, doc_id)
                VALUES ('delete', ?, ?, ?, ?, ?)
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement selectPs = conn.prepareStatement(selectSql)) {
            bindParams(selectPs, params);
            try (ResultSet rs = selectPs.executeQuery()) {
                while (rs.next()) {
                    try (PreparedStatement deletePs = conn.prepareStatement(deleteSql)) {
                        deletePs.setString(1, rs.getString("title"));
                        deletePs.setString(2, rs.getString("content"));
                        deletePs.setLong(3, rs.getLong("chunk_id"));
                        deletePs.setLong(4, rs.getLong("kb_id"));
                        deletePs.setLong(5, rs.getLong("doc_id"));
                        deletePs.executeUpdate();
                    } catch (SQLException rowError) {
                        log.warn("跳过 FTS 行删除 chunkId={}: {}", rs.getLong("chunk_id"), rowError.getMessage());
                    }
                }
            }
        } catch (SQLException e) {
            throw new IOException("SQLite FTS 删除失败", e);
        }
    }

    private void executeUpdate(String sql, Object... params) throws IOException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, params);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IOException("SQLite 向量删除失败", e);
        }
    }

    private void bindParams(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object value = params[i];
            if (value instanceof Long l) {
                ps.setLong(i + 1, l);
            } else if (value instanceof Integer n) {
                ps.setInt(i + 1, n);
            } else {
                ps.setObject(i + 1, value);
            }
        }
    }

    private String sanitizeFtsQuery(String queryText) {
        return queryText.replace("\"", "\"\"");
    }
}
