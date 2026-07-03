package com.fast.knowledge.langchain4j;

import com.fast.knowledge.config.KnowledgeProperties;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Slf4j
public class KbEmbeddingStore implements dev.langchain4j.store.embedding.EmbeddingStore<TextSegment> {

    public static final String META_KB_ID = "kbId";
    public static final String META_DOC_ID = "docId";
    public static final String META_CHUNK_ID = "chunkId";
    public static final String META_TITLE = "title";
    public static final String META_DOC_TYPE = "docType";
    public static final String META_DOC_NO = "docNo";
    public static final String META_SECTION = "section";

    private final Long kbId;
    private final PgVectorEmbeddingStore delegate;
    private final KnowledgeProperties properties;

    public KbEmbeddingStore(Long kbId, PgVectorEmbeddingStore delegate, KnowledgeProperties properties) {
        this.kbId = kbId;
        this.delegate = delegate;
        this.properties = properties;
    }

    @Override
    public String add(Embedding embedding) {
        throw new UnsupportedOperationException("请使用 add(embedding, textSegment) 并附带元数据");
    }

    @Override
    public void add(String id, Embedding embedding) {
        throw new UnsupportedOperationException("请使用 add(id, embedding, textSegment)");
    }

    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        String id = resolveId(textSegment);
        addAll(List.of(id), List.of(embedding), List.of(textSegment));
        return id;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        throw new UnsupportedOperationException("请使用 addAll(ids, embeddings, segments)");
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> segments) {
        if (embeddings.size() != segments.size() || ids.size() != segments.size()) {
            throw new IllegalArgumentException("ids、embeddings 与 segments 数量不一致");
        }
        List<TextSegment> enriched = new ArrayList<>(segments.size());
        for (TextSegment segment : segments) {
            enriched.add(withKbMetadata(segment));
        }
        delegate.addAll(ids, embeddings, enriched);
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        if (embeddings.size() != embedded.size()) {
            throw new IllegalArgumentException("embeddings 与 segments 数量不一致");
        }
        List<String> ids = new ArrayList<>(embedded.size());
        for (TextSegment segment : embedded) {
            ids.add(resolveId(segment));
        }
        addAll(ids, embeddings, embedded);
        return ids;
    }

    @Override
    public void remove(String id) {
        delegate.remove(id);
    }

    @Override
    public void removeAll(Collection<String> ids) {
        delegate.removeAll(ids);
    }

    @Override
    public void removeAll(Filter filter) {
        delegate.removeAll(filter);
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        return search(request, null);
    }

    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request, String docType) {
        int maxResults = request.maxResults() > 0
                ? request.maxResults()
                : properties.getSearch().getDefaultTopK();
        Filter filter = kbFilter();
        if (docType != null && !docType.isBlank()) {
            filter = Filter.and(filter, metadataKey(META_DOC_TYPE).isEqualTo(docType));
        }
        EmbeddingSearchRequest scoped = EmbeddingSearchRequest.builder()
                .queryEmbedding(request.queryEmbedding())
                .query(request.query())
                .maxResults(maxResults)
                .minScore(request.minScore())
                .filter(filter)
                .build();
        return delegate.search(scoped);
    }

    private Filter kbFilter() {
        return metadataKey(META_KB_ID).isEqualTo(kbId);
    }

    private TextSegment withKbMetadata(TextSegment segment) {
        Metadata metadata = segment.metadata() != null
                ? segment.metadata().copy()
                : new Metadata();
        metadata.put(META_KB_ID, kbId);
        ensureRequiredMetadata(metadata);
        return TextSegment.from(segment.text(), metadata);
    }

    private void ensureKbMetadata(TextSegment segment) {
        Metadata metadata = segment.metadata();
        if (metadata == null) {
            throw new IllegalArgumentException("TextSegment 需包含元数据");
        }
        metadata.put(META_KB_ID, kbId);
        ensureRequiredMetadata(metadata);
    }

    private void ensureRequiredMetadata(Metadata metadata) {
        if (metadata.getLong(META_DOC_ID) == null || metadata.getLong(META_CHUNK_ID) == null) {
            throw new IllegalArgumentException("TextSegment 元数据需包含 docId 与 chunkId");
        }
    }

    private String resolveId(TextSegment segment) {
        ensureKbMetadata(segment);
        return UUID.randomUUID().toString();
    }

    public Long kbId() {
        return kbId;
    }

    public static SearchHitMapper.Hit fromMatch(EmbeddingMatch<TextSegment> match, Long kbId) {
        TextSegment segment = match.embedded();
        Metadata metadata = segment.metadata();
        SearchHitMapper.Hit hit = new SearchHitMapper.Hit();
        hit.setKbId(kbId);
        hit.setChunkId(metadata.getLong(META_CHUNK_ID));
        hit.setDocumentId(metadata.getLong(META_DOC_ID));
        hit.setTitle(metadata.getString(META_TITLE));
        hit.setDocType(metadata.getString(META_DOC_TYPE));
        hit.setDocNo(metadata.getString(META_DOC_NO));
        hit.setSection(metadata.getString(META_SECTION));
        hit.setContent(segment.text());
        hit.setScore(match.score());
        return hit;
    }
}
