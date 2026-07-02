package com.fast.knowledge.vector.pgvector;

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
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@ConditionalOnProperty(name = "knowledge.vector.provider", havingValue = "pgvector")
public class PgVectorStore implements VectorStore {

    private final DataSource dataSource;

    public PgVectorStore(DataSource dataSource) {
        this.dataSource = dataSource;
        log.info("PostgreSQL + pgvector 向量存储已启用（复用主数据源）");
    }

    @Override
    public void addChunks(Long kbId, List<VectorChunk> chunks) throws IOException {
        String sql = """
                INSERT INTO kb_vector_chunk (chunk_id, kb_id, doc_id, title, content, embedding, search_text)
                VALUES (?, ?, ?, ?, ?, ?::vector, to_tsvector('simple', coalesce(?, '') || ' ' || ?))
                ON CONFLICT (chunk_id) DO UPDATE SET
                    kb_id = EXCLUDED.kb_id,
                    doc_id = EXCLUDED.doc_id,
                    title = EXCLUDED.title,
                    content = EXCLUDED.content,
                    embedding = EXCLUDED.embedding,
                    search_text = EXCLUDED.search_text
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (VectorChunk chunk : chunks) {
                String title = chunk.getTitle() != null ? chunk.getTitle() : "";
                ps.setLong(1, chunk.getChunkId());
                ps.setLong(2, kbId);
                ps.setLong(3, chunk.getDocId());
                ps.setString(4, title);
                ps.setString(5, chunk.getContent());
                ps.setString(6, toPgVector(chunk.getVector()));
                ps.setString(7, title);
                ps.setString(8, chunk.getContent());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new IOException("PgVector 写入失败", e);
        }
    }

    @Override
    public void deleteByDocument(Long kbId, Long docId) throws IOException {
        executeUpdate("DELETE FROM kb_vector_chunk WHERE kb_id = ? AND doc_id = ?", kbId, docId);
    }

    @Override
    public void deleteChunk(Long kbId, Long chunkId) throws IOException {
        executeUpdate("DELETE FROM kb_vector_chunk WHERE kb_id = ? AND chunk_id = ?", kbId, chunkId);
    }

    @Override
    public void deleteKb(Long kbId) throws IOException {
        executeUpdate("DELETE FROM kb_vector_chunk WHERE kb_id = ?", kbId);
    }

    @Override
    public List<SearchHit> hybridSearch(Long kbId, String queryText, float[] queryVector,
                                        int topK, double alpha) throws IOException {
        int candidateK = Math.max(topK * 2, topK);
        List<SearchHit> vectorHits = vectorSearch(kbId, queryVector, candidateK);
        List<SearchHit> textHits = textSearch(kbId, queryText, candidateK);
        return HybridSearchMerger.merge(vectorHits, textHits, alpha, topK);
    }

    private List<SearchHit> vectorSearch(Long kbId, float[] queryVector, int limit) throws IOException {
        String sql = """
                SELECT chunk_id, doc_id, title, content,
                       1 - (embedding <=> ?::vector) AS score
                FROM kb_vector_chunk
                WHERE kb_id = ?
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String vec = toPgVector(queryVector);
            ps.setString(1, vec);
            ps.setLong(2, kbId);
            ps.setString(3, vec);
            ps.setInt(4, limit);
            return readHits(kbId, ps);
        } catch (SQLException e) {
            throw new IOException("PgVector 检索失败: " + e.getMessage(), e);
        }
    }

    private List<SearchHit> textSearch(Long kbId, String queryText, int limit) throws IOException {
        if (queryText == null || queryText.isBlank()) {
            return List.of();
        }
        String sql = """
                SELECT chunk_id, doc_id, title, content,
                       ts_rank(search_text, plainto_tsquery('simple', ?)) AS score
                FROM kb_vector_chunk
                WHERE kb_id = ?
                  AND search_text @@ plainto_tsquery('simple', ?)
                ORDER BY score DESC
                LIMIT ?
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, queryText);
            ps.setLong(2, kbId);
            ps.setString(3, queryText);
            ps.setInt(4, limit);
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

    private void executeUpdate(String sql, Object... params) throws IOException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, params);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IOException("PgVector 删除失败", e);
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

    private String toPgVector(float[] vector) {
        return "[" + java.util.Arrays.stream(toDoubleArray(vector))
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(",")) + "]";
    }

    private double[] toDoubleArray(float[] vector) {
        double[] result = new double[vector.length];
        for (int i = 0; i < vector.length; i++) {
            result[i] = vector[i];
        }
        return result;
    }
}
