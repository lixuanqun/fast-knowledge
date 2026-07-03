package com.fast.knowledge.langchain4j;

import com.fast.knowledge.model.vo.SearchHitVO;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;

import java.util.List;

public final class RetrievedContentMapper {

    private RetrievedContentMapper() {
    }

    public static SearchHitVO toSearchHit(Content content) {
        TextSegment segment = content.textSegment();
        Metadata metadata = segment.metadata();
        SearchHitVO vo = new SearchHitVO();
        if (metadata != null) {
            vo.setChunkId(metadata.getLong(KbEmbeddingStore.META_CHUNK_ID));
            vo.setDocumentId(metadata.getLong(KbEmbeddingStore.META_DOC_ID));
            vo.setDocumentTitle(metadata.getString(KbEmbeddingStore.META_TITLE));
            vo.setDocType(metadata.getString(KbEmbeddingStore.META_DOC_TYPE));
            vo.setDocNo(metadata.getString(KbEmbeddingStore.META_DOC_NO));
            vo.setSection(metadata.getString(KbEmbeddingStore.META_SECTION));
        }
        vo.setContent(segment.text());
        return vo;
    }

    public static List<SearchHitVO> toSearchHits(List<Content> contents) {
        return contents.stream().map(RetrievedContentMapper::toSearchHit).toList();
    }
}
