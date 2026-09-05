package com.fast.knowledge.langchain4j.store;

import com.fast.knowledge.config.KnowledgeProperties;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * LangChain4j PgVector 向量库配置 — HYBRID 检索 + 可选 HNSW/IVFFlat 索引。
 * <p>索引创建延迟到 ApplicationReadyEvent，避免阻塞 Spring 容器启动。
 * <p>仅 Standard 形态（knowledge.vector.provider=pgvector，默认）装配；
 * Classic 形态（local）下不创建该 Bean，避免在 MySQL 数据源上执行 PG 专属 SQL。
 */
@Configuration
@ConditionalOnProperty(name = "knowledge.vector.provider", havingValue = "pgvector", matchIfMissing = true)
public class PgVectorEmbeddingStoreConfig {

    private static final Logger log = LoggerFactory.getLogger(PgVectorEmbeddingStoreConfig.class);

    private final DataSource dataSource;
    private final KnowledgeProperties.PgVector pgConfig;
    private final KnowledgeProperties properties;

    public PgVectorEmbeddingStoreConfig(DataSource dataSource, KnowledgeProperties properties) {
        this.dataSource = dataSource;
        this.pgConfig = properties.getVector().getPgvector();
        this.properties = properties;
    }

    @Bean
    public PgVectorEmbeddingStore pgVectorEmbeddingStore(KnowledgeProperties properties) {
        KnowledgeProperties.PgVector pg = properties.getVector().getPgvector();
        KnowledgeProperties.Embedding embedding = properties.getEmbedding();

        PgVectorEmbeddingStore.SearchMode mode = "HYBRID".equalsIgnoreCase(pg.getSearchMode())
                ? PgVectorEmbeddingStore.SearchMode.HYBRID
                : PgVectorEmbeddingStore.SearchMode.VECTOR;

        return PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(dataSource)
                .table(pg.getTable())
                .dimension(embedding.getDimension())
                .searchMode(mode)
                .rrfK(pg.getRrfK())
                .build();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void createIndexOnStartup() {
        validateDimensionConsistency();
        String indexType = pgConfig.getIndexType();
        if (indexType == null || indexType.isBlank()) {
            return;
        }

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            String sql;
            if ("hnsw".equalsIgnoreCase(indexType)) {
                int m = Math.max(2, pgConfig.getHnswM());
                int ef = Math.max(10, pgConfig.getHnswEfConstruction());
                sql = String.format(
                        "CREATE INDEX IF NOT EXISTS idx_%s_embedding_hnsw ON %s "
                        + "USING hnsw (embedding vector_cosine_ops) "
                        + "WITH (m = %d, ef_construction = %d)",
                        pgConfig.getTable(), pgConfig.getTable(), m, ef);
                log.info("Creating HNSW index on {} (m={}, ef_construction={})",
                        pgConfig.getTable(), m, ef);
            } else if ("ivfflat".equalsIgnoreCase(indexType)) {
                sql = String.format(
                        "CREATE INDEX IF NOT EXISTS idx_%s_embedding_ivfflat ON %s "
                        + "USING ivfflat (embedding vector_cosine_ops) "
                        + "WITH (lists = 100)",
                        pgConfig.getTable(), pgConfig.getTable());
                log.info("Creating IVFFlat index on {}", pgConfig.getTable());
            } else {
                return;
            }

            stmt.execute(sql);
        } catch (Exception e) {
            log.warn("Index creation skipped (table may not exist yet): {}", e.getMessage());
        }
    }

    /**
     * 校验存量 pgvector 表维度与当前配置一致。pgvector 列维度建表后不可变，
     * 不一致时索引会以晦涩的「value too long for character varying(N)」失败——
     * 此处提前给出可操作指引（不同步则后续索引必然失败）。
     */
    private void validateDimensionConsistency() {
        String table = pgConfig.getTable();
        int configured = properties.getEmbedding().getDimension();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             var rs = stmt.executeQuery(
                     "SELECT atttypmod FROM pg_attribute "
                     + "WHERE attrelid = '" + table + "'::regclass AND attname = 'embedding' AND atttypmod > 0")) {
            if (rs.next()) {
                int tableDim = rs.getInt(1);
                if (configured > 0 && tableDim != configured) {
                    log.error("向量维度不一致：{}.embedding=vector({})，当前配置 knowledge.embedding.dimension={}。"
                            + "pgvector 列维度建表后不可变：请将 EMBEDDING_DIMENSION 设为 {} 以匹配存量表，"
                            + "或改用新维度并重建向量表后全量重索引。", table, tableDim, configured, tableDim);
                }
            }
        } catch (Exception e) {
            log.warn("向量维度校验跳过（表可能尚未创建）: {}", e.getMessage());
        }
    }
}
