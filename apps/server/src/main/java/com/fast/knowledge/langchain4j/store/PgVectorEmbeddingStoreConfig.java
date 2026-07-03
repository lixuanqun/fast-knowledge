package com.fast.knowledge.langchain4j.store;



import com.fast.knowledge.config.KnowledgeProperties;

import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;

import org.springframework.context.annotation.Bean;

import org.springframework.context.annotation.Configuration;



import javax.sql.DataSource;



/**

 * LangChain4j 官方 PgVector 向量库（HYBRID 检索），复用 Spring 数据源。

 */

@Configuration

public class PgVectorEmbeddingStoreConfig {



    @Bean

    public PgVectorEmbeddingStore pgVectorEmbeddingStore(DataSource dataSource,

                                                         KnowledgeProperties properties) {

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

}

