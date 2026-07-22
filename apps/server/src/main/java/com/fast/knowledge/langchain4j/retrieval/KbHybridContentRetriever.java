package com.fast.knowledge.langchain4j.retrieval;

import com.fast.knowledge.langchain4j.KbEmbeddingStore;
import com.fast.knowledge.model.vo.SearchHitVO;
import com.fast.knowledge.service.WikiAwareRetrievalService;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KbHybridContentRetriever implements ContentRetriever {

    private final Long kbId;
    private final WikiAwareRetrievalService wikiAwareRetrievalService;

    public KbHybridContentRetriever(Long kbId, WikiAwareRetrievalService wikiAwareRetrievalService) {
        this.kbId = kbId;
        this.wikiAwareRetrievalService = wikiAwareRetrievalService;
    }

    @Override
    public List<Content> retrieve(Query query) {
        try {
            return wikiAwareRetrievalService.retrieve(kbId, query.text()).stream()
                    .map(this::toContent)
                    .toList();
        } catch (Exception e) {
            throw new RuntimeException("知识库检索失败", e);
        }
    }

    private Content toContent(SearchHitVO hit) {
        Map<String, Object> meta = new HashMap<>();
        meta.put(KbEmbeddingStore.META_KB_ID, kbId);
        meta.put(KbEmbeddingStore.META_DOC_ID, hit.getDocumentId() != null ? hit.getDocumentId() : 0L);
        meta.put(KbEmbeddingStore.META_CHUNK_ID, hit.getChunkId() != null ? hit.getChunkId() : 0L);
        meta.put(KbEmbeddingStore.META_TITLE, hit.getDocumentTitle() != null ? hit.getDocumentTitle() : "");
        meta.put("score", hit.getScore());
        if (hit.getDocType() != null) {
            meta.put("docType", hit.getDocType());
        }
        if (hit.getSection() != null) {
            meta.put("section", hit.getSection());
        }
        Metadata metadata = Metadata.from(meta);
        TextSegment segment = TextSegment.from(
                "【" + hit.getDocumentTitle() + "】\n" + hit.getContent(),
                metadata
        );
        return Content.from(segment);
    }
}
