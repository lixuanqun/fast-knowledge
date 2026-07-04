package com.fast.knowledge.langchain4j.store;

import com.fast.knowledge.config.KnowledgeProperties;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * LangChain4j PgVector 向量库配置 — HYBRID 检索 + 可选 HNSW/IVFFlat 索引。
 */
@Configuration
public class PgVectorEmbeddingStoreConfig {

    private static final Logger log = LoggerFactory.getLogger(PgVectorEmbeddingStoreConfig.class);

    @Bean
    public PgVectorEmbeddingStore pgVectorEmbeddingStore(DataSource dataSource,
                                                         KnowledgeProperties properties) {
        KnowledgeProperties.PgVector pg = properties.getVector().getPgvector();
        KnowledgeProperties.Embedding embedding = properties.getEmbedding();

        PgVectorEmbeddingStore.SearchMode mode = "HYBRID".equalsIgnoreCase(pg.getSearchMode())
                ? PgVectorEmbeddingStore.SearchMode.HYBRID
                : PgVectorEmbeddingStore.SearchMode.VECTOR;

        PgVectorEmbeddingStore store = PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(dataSource)
                .table(pg.getTable())
                .dimension(embedding.getDimension())
                .searchMode(mode)
                .rrfK(pg.getRrfK())
                .build();

        // Create optimized index on startup
        try {
            createIndex(dataSource, pg);
        } catch (Exception e) {
            log.warn("Failed to create vector index, will use default: {}", e.getMessage());
        }

        return store;
    }

    private void createIndex(DataSource dataSource, KnowledgeProperties.PgVector pg) {
        String indexType = pg.getIndexType();
        if (indexType == null || indexType.isBlank()) {
            return;
        }

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            String sql;
            if ("hnsw".equalsIgnoreCase(indexType)) {
                int m = Math.max(2, pg.getHnswM());
                int ef = Math.max(10, pg.getHnswEfConstruction());
                sql = String.format(
                        "CREATE INDEX IF NOT EXISTS idx_%s_embedding_hnsw ON %s "
                        + "USING hnsw (embedding vector_cosine_ops) "
                        + "WITH (m = %d, ef_construction = %d)",
                        pg.getTable(), pg.getTable(), m, ef);
                log.info("Creating HNSW index on {} (m={}, ef_construction={})",
                        pg.getTable(), m, ef);
            } else if ("ivfflat".equalsIgnoreCase(indexType)) {
                sql = String.format(
                        "CREATE INDEX IF NOT EXISTS idx_%s_embedding_ivfflat ON %s "
                        + "USING ivfflat (embedding vector_cosine_ops) "
                        + "WITH (lists = 100)",
                        pg.getTable(), pg.getTable());
                log.info("Creating IVFFlat index on {}", pg.getTable());
            } else {
                return;
            }

            stmt.execute(sql);
        } catch (Exception e) {
            log.warn("Index creation skipped (table may not exist yet): {}", e.getMessage());
        }
    }
}
