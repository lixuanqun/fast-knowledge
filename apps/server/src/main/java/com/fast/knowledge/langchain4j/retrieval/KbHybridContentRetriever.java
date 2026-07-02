package com.fast.knowledge.langchain4j.retrieval;

import com.fast.knowledge.langchain4j.KbEmbeddingStore;
import com.fast.knowledge.model.dto.SearchRequest;
import com.fast.knowledge.model.vo.SearchHitVO;
import com.fast.knowledge.service.SearchService;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;

import java.util.List;
import java.util.Map;

public class KbHybridContentRetriever implements ContentRetriever {

    private final Long kbId;
    private final SearchService searchService;

    public KbHybridContentRetriever(Long kbId, SearchService searchService) {
        this.kbId = kbId;
        this.searchService = searchService;
    }

    @Override
    public List<Content> retrieve(Query query) {
        try {
            SearchRequest request = new SearchRequest();
            request.setKbId(kbId);
            request.setQuery(query.text());

            return searchService.search(request).stream().map(this::toContent).toList();
        } catch (Exception e) {
            throw new RuntimeException("知识库检索失败", e);
        }
    }

    private Content toContent(SearchHitVO hit) {
        Metadata metadata = Metadata.from(Map.of(
                KbEmbeddingStore.META_KB_ID, kbId,
                KbEmbeddingStore.META_DOC_ID, hit.getDocumentId(),
                KbEmbeddingStore.META_CHUNK_ID, hit.getChunkId(),
                KbEmbeddingStore.META_TITLE, hit.getDocumentTitle() != null ? hit.getDocumentTitle() : "",
                "score", hit.getScore()
        ));
        TextSegment segment = TextSegment.from(
                "【" + hit.getDocumentTitle() + "】\n" + hit.getContent(),
                metadata
        );
        return Content.from(segment);
    }
}
