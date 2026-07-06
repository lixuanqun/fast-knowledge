package com.fast.knowledge.langchain4j.store;

import com.fast.knowledge.config.KnowledgeProperties;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 */
@Configuration
public class PgVectorEmbeddingStoreConfig {

    private static final Logger log = LoggerFactory.getLogger(PgVectorEmbeddingStoreConfig.class);

    private final DataSource dataSource;
    private final KnowledgeProperties.PgVector pgConfig;

    public PgVectorEmbeddingStoreConfig(DataSource dataSource, KnowledgeProperties properties) {
        this.dataSource = dataSource;
        this.pgConfig = properties.getVector().getPgvector();
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
}
