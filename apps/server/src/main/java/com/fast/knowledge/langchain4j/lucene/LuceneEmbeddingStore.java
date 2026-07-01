package com.fast.knowledge.langchain4j.lucene;

import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.vector.VectorChunk;
import com.fast.knowledge.vector.VectorStore;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;

import com.fast.knowledge.vector.SearchHit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Slf4j
public class LuceneEmbeddingStore implements EmbeddingStore<TextSegment> {

    public static final String META_KB_ID = "kbId";
    public static final String META_DOC_ID = "docId";
    public static final String META_CHUNK_ID = "chunkId";
    public static final String META_TITLE = "title";

    private final Long kbId;
    private final VectorStore vectorStore;
    private final KnowledgeProperties properties;

    public LuceneEmbeddingStore(Long kbId, VectorStore vectorStore, KnowledgeProperties properties) {
        this.kbId = kbId;
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    @Override
    public String add(Embedding embedding) {
        throw new UnsupportedOperationException("请使用 add(embedding, textSegment) 并附带元数据");
    }

    @Override
    public void add(String id, Embedding embedding) {
        throw new UnsupportedOperationException("请使用 add(embedding, textSegment) 并附带元数据");
    }

    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        try {
            vectorStore.addChunks(kbId, List.of(toVectorChunk(embedding, textSegment)));
            Metadata metadata = textSegment.metadata();
            return String.valueOf(metadata.getLong(META_CHUNK_ID));
        } catch (Exception e) {
            throw new RuntimeException("写入向量索引失败", e);
        }
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        throw new UnsupportedOperationException("请使用 addAll(embeddings, segments)");
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        if (embeddings.size() != embedded.size()) {
            throw new IllegalArgumentException("embeddings 与 segments 数量不一致");
        }
        try {
            List<String> ids = new ArrayList<>(embeddings.size());
            List<VectorChunk> chunks = new ArrayList<>(embeddings.size());
            for (int i = 0; i < embeddings.size(); i++) {
                TextSegment textSegment = embedded.get(i);
                chunks.add(toVectorChunk(embeddings.get(i), textSegment));
                ids.add(String.valueOf(textSegment.metadata().getLong(META_CHUNK_ID)));
            }
            vectorStore.addChunks(kbId, chunks);
            return ids;
        } catch (Exception e) {
            throw new RuntimeException("批量写入向量索引失败", e);
        }
    }

    @Override
    public void remove(String id) {
        try {
            vectorStore.deleteChunk(kbId, Long.parseLong(id));
        } catch (Exception e) {
            log.warn("删除分块失败 chunkId={}: {}", id, e.getMessage());
        }
    }

    @Override
    public void removeAll(Collection<String> ids) {
        ids.forEach(this::remove);
    }

    @Override
    public void removeAll(dev.langchain4j.store.embedding.filter.Filter filter) {
        throw new UnsupportedOperationException("内置向量库暂不支持按 Filter 批量删除");
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        try {
            int maxResults = request.maxResults() > 0
                    ? request.maxResults()
                    : properties.getSearch().getDefaultTopK();
            double alpha = properties.getSearch().getHybridAlpha();
            float[] vector = request.queryEmbedding().vector();

            List<SearchHit> hits = vectorStore.hybridSearch(kbId, "", vector, maxResults, alpha);

            List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();
            for (SearchHit hit : hits) {
                Metadata metadata = Metadata.from(Map.of(
                        META_KB_ID, hit.getKbId(),
                        META_DOC_ID, hit.getDocumentId(),
                        META_CHUNK_ID, hit.getChunkId(),
                        META_TITLE, hit.getTitle() != null ? hit.getTitle() : ""
                ));
                TextSegment segment = TextSegment.from(hit.getContent(), metadata);
                matches.add(new EmbeddingMatch<>(hit.getScore(), String.valueOf(hit.getChunkId()), null, segment));
            }
            return new EmbeddingSearchResult<>(matches);
        } catch (Exception e) {
            throw new RuntimeException("向量检索失败", e);
        }
    }

    private VectorChunk toVectorChunk(Embedding embedding, TextSegment textSegment) {
        Metadata metadata = textSegment.metadata();
        Long docId = metadata.getLong(META_DOC_ID);
        Long chunkId = metadata.getLong(META_CHUNK_ID);
        String title = metadata.getString(META_TITLE);
        if (docId == null || chunkId == null) {
            throw new IllegalArgumentException("TextSegment 元数据需包含 docId 与 chunkId");
        }
        VectorChunk chunk = new VectorChunk();
        chunk.setDocId(docId);
        chunk.setChunkId(chunkId);
        chunk.setTitle(title != null ? title : "");
        chunk.setContent(textSegment.text());
        chunk.setVector(embedding.vector());
        return chunk;
    }

    public Long kbId() {
        return kbId;
    }
}
