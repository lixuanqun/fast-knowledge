package com.fast.knowledge.vector;

import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.vector.lucene.LuceneVectorStore;
import com.fast.knowledge.vector.pgvector.PgVectorStore;
import com.fast.knowledge.vector.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class VectorStoreConfig {

    @Bean
    @Primary
    public VectorStore vectorStore(KnowledgeProperties properties,
                                   LuceneVectorStore luceneVectorStore,
                                   ObjectProvider<PgVectorStore> pgVectorStore,
                                   ObjectProvider<QdrantVectorStore> qdrantVectorStore) {
        String provider = properties.getVector().getProvider();
        if ("pgvector".equalsIgnoreCase(provider)) {
            PgVectorStore pg = pgVectorStore.getIfAvailable();
            if (pg != null) {
                return pg;
            }
        }
        if ("qdrant".equalsIgnoreCase(provider)) {
            QdrantVectorStore qdrant = qdrantVectorStore.getIfAvailable();
            if (qdrant != null) {
                return qdrant;
            }
        }
        return luceneVectorStore;
    }
}
