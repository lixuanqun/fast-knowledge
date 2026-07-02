package com.fast.knowledge.vector;

import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.vector.pgvector.PgVectorStore;
import com.fast.knowledge.vector.sqlitevec.SqliteVecVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class VectorStoreConfig {

    @Bean
    @Primary
    public VectorStore vectorStore(KnowledgeProperties properties,
                                   ObjectProvider<SqliteVecVectorStore> sqliteVecVectorStore,
                                   ObjectProvider<PgVectorStore> pgVectorStore) {
        String provider = properties.getVector().getProvider();
        if ("sqlite-vec".equalsIgnoreCase(provider)) {
            SqliteVecVectorStore sqlite = sqliteVecVectorStore.getIfAvailable();
            if (sqlite != null) {
                return sqlite;
            }
        }
        if ("pgvector".equalsIgnoreCase(provider)) {
            PgVectorStore pg = pgVectorStore.getIfAvailable();
            if (pg != null) {
                return pg;
            }
        }
        SqliteVecVectorStore sqlite = sqliteVecVectorStore.getIfAvailable();
        if (sqlite != null) {
            return sqlite;
        }
        PgVectorStore pg = pgVectorStore.getIfAvailable();
        if (pg != null) {
            return pg;
        }
        throw new IllegalStateException("未找到可用的 VectorStore 实现，请检查 knowledge.vector.provider 配置");
    }
}
