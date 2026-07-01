package com.fast.knowledge.vector.pgvector;

import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.vector.SearchHit;
import com.fast.knowledge.vector.VectorChunk;
import com.fast.knowledge.vector.VectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@ConditionalOnProperty(name = "knowledge.vector.provider", havingValue = "pgvector")
public class PgVectorStore implements VectorStore {

    private final KnowledgeProperties properties;

    public PgVectorStore(KnowledgeProperties properties) {
        this.properties = properties;
        log.info("PgVector 向量存储已启用");
    }

    private Connection connect() throws Exception {
        var pg = properties.getVector().getPgvector();
        return DriverManager.getConnection(pg.getDatasourceUrl(), pg.getUsername(), pg.getPassword());
    }

    @Override
    public void addChunks(Long kbId, List<VectorChunk> chunks) throws IOException {
        String sql = """
                INSERT INTO kb_vector_chunk (kb_id, doc_id, chunk_id, title, content, embedding)
                VALUES (?, ?, ?, ?, ?, ?::vector)
                ON CONFLICT (chunk_id) DO UPDATE SET
                    title = EXCLUDED.title, content = EXCLUDED.content, embedding = EXCLUDED.embedding
                """;
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (VectorChunk chunk : chunks) {
                ps.setLong(1, kbId);
                ps.setLong(2, chunk.getDocId());
                ps.setLong(3, chunk.getChunkId());
                ps.setString(4, chunk.getTitle());
                ps.setString(5, chunk.getContent());
                ps.setString(6, toPgVector(chunk.getVector()));
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (Exception e) {
            throw new IOException("PgVector 写入失败", e);
        }
    }

    @Override
    public void deleteByDocument(Long kbId, Long docId) throws IOException {
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM kb_vector_chunk WHERE kb_id = ? AND doc_id = ?")) {
            ps.setLong(1, kbId);
            ps.setLong(2, docId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IOException("PgVector 删除文档失败", e);
        }
    }

    @Override
    public void deleteChunk(Long kbId, Long chunkId) throws IOException {
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM kb_vector_chunk WHERE kb_id = ? AND chunk_id = ?")) {
            ps.setLong(1, kbId);
            ps.setLong(2, chunkId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IOException("PgVector 删除分块失败", e);
        }
    }

    @Override
    public void deleteKb(Long kbId) throws IOException {
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM kb_vector_chunk WHERE kb_id = ?")) {
            ps.setLong(1, kbId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IOException("PgVector 删除知识库失败", e);
        }
    }

    @Override
    public List<SearchHit> hybridSearch(Long kbId, String queryText, float[] queryVector,
                                        int topK, double alpha) throws IOException {
        String sql = """
                SELECT chunk_id, doc_id, title, content,
                       1 - (embedding <=> ?::vector) AS score
                FROM kb_vector_chunk
                WHERE kb_id = ?
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """;
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String vec = toPgVector(queryVector);
            ps.setString(1, vec);
            ps.setLong(2, kbId);
            ps.setString(3, vec);
            ps.setInt(4, topK);
            List<SearchHit> hits = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SearchHit hit = new SearchHit();
                    hit.setKbId(kbId);
                    hit.setChunkId(rs.getLong("chunk_id"));
                    hit.setDocumentId(rs.getLong("doc_id"));
                    hit.setTitle(rs.getString("title"));
                    hit.setContent(rs.getString("content"));
                    hit.setScore(rs.getDouble("score") * alpha);
                    hits.add(hit);
                }
            }
            return hits;
        } catch (Exception e) {
            throw new IOException("PgVector 检索失败: " + e.getMessage(), e);
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
