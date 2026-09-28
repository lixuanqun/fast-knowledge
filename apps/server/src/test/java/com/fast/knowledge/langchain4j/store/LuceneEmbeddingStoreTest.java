package com.fast.knowledge.langchain4j.store;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.filter.Filter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LuceneEmbeddingStoreTest {

    @TempDir
    Path tempDir;

    private TextSegment segment(String text, long kbId, long docId, long chunkId, String docType) {
        Metadata metadata = Metadata.from(Map.of(
                "kbId", kbId,
                "docId", docId,
                "chunkId", chunkId,
                "title", "制度" + docId,
                "docType", docType));
        return TextSegment.from(text, metadata);
    }

    @Test
    void searchRanksByCosineAndScoreNormalized() {
        LuceneEmbeddingStore store = LuceneEmbeddingStore.open(tempDir.resolve("idx"));
        store.addAll(List.of("a", "b", "c"),
                List.of(new Embedding(new float[] {1, 0, 0}),
                        new Embedding(new float[] {0, 1, 0}),
                        new Embedding(new float[] {0.9f, 0.44f, 0})),
                List.of(segment("甲", 1, 10, 100, "制度"),
                        segment("乙", 1, 11, 101, "制度"),
                        segment("丙", 1, 12, 102, "工艺")));

        EmbeddingSearchResult<TextSegment> result = store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(new Embedding(new float[] {1, 0, 0}))
                .maxResults(3)
                .build());

        assertEquals("a", result.matches().get(0).embeddingId());
        // 完全同向：分数 = (1+cos)/2 = 1.0
        assertEquals(1.0, result.matches().get(0).score(), 1e-6);
        assertTrue(result.matches().get(2).score() < result.matches().get(0).score());
        // 元数据类型保真：chunkId 以 Long 读回
        assertEquals(100L, result.matches().get(0).embedded().metadata().getLong("chunkId"));
        assertEquals("甲", result.matches().get(0).embedded().text());
        assertNull(result.matches().get(0).embedding());
        store.close();
    }

    @Test
    void filterByMetadata() {
        LuceneEmbeddingStore store = LuceneEmbeddingStore.open(tempDir.resolve("idx-filter"));
        store.addAll(List.of("a", "b"),
                List.of(new Embedding(new float[] {1, 0, 0}), new Embedding(new float[] {0.9f, 0.1f, 0})),
                List.of(segment("甲", 1, 10, 100, "制度"),
                        segment("丙", 1, 12, 102, "工艺")));

        Filter docTypeFilter = metadataKey("kbId").isEqualTo(1L)
                .and(metadataKey("docType").isEqualTo("工艺"));
        EmbeddingSearchResult<TextSegment> result = store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(new Embedding(new float[] {1, 0, 0}))
                .maxResults(5)
                .filter(docTypeFilter)
                .build());

        assertEquals(1, result.matches().size());
        assertEquals("丙", result.matches().get(0).embedded().text());
        store.close();
    }

    @Test
    void removeAllByFilterAndPersistAcrossReopen() {
        Path dir = tempDir.resolve("idx-persist");
        LuceneEmbeddingStore store = LuceneEmbeddingStore.open(dir);
        store.addAll(List.of("a", "b"),
                List.of(new Embedding(new float[] {1, 0, 0}), new Embedding(new float[] {0, 1, 0})),
                List.of(segment("甲", 1, 10, 100, "制度"),
                        segment("乙", 1, 11, 101, "制度")));
        store.removeAll(metadataKey("kbId").isEqualTo(1L)
                .and(metadataKey("docId").isEqualTo(10L)));
        store.flush();
        store.close();

        LuceneEmbeddingStore reopened = LuceneEmbeddingStore.open(dir);
        EmbeddingSearchResult<TextSegment> result = reopened.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(new Embedding(new float[] {1, 0, 0}))
                .maxResults(5)
                .filter(metadataKey("kbId").isEqualTo(1L))
                .build());
        assertEquals(1, result.matches().size());
        assertEquals("乙", result.matches().get(0).embedded().text());
        reopened.close();
    }

    @Test
    void emptyIndexSearchReturnsEmpty() {
        LuceneEmbeddingStore store = LuceneEmbeddingStore.open(tempDir.resolve("idx-empty"));
        EmbeddingSearchResult<TextSegment> result = store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(new Embedding(new float[] {1, 0, 0}))
                .maxResults(3)
                .filter(metadataKey("kbId").isEqualTo(9L))
                .build());
        assertEquals(0, result.matches().size());
        store.close();
    }

    @Test
    void unsupportedFilterShapeIsRejected() {
        LuceneEmbeddingStore store = LuceneEmbeddingStore.open(tempDir.resolve("idx-unsupported"));
        Filter gt = metadataKey("pageNo").isGreaterThan(3);
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> store.toQuery(gt));
        store.close();
    }
}
