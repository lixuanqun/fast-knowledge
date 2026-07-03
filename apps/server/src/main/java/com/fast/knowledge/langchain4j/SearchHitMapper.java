package com.fast.knowledge.langchain4j;

import com.fast.knowledge.model.vo.SearchHitVO;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.data.segment.TextSegment;
import lombok.Data;

/**
 * 将 LangChain4j 检索结果映射为 API 层 VO。
 */
public final class SearchHitMapper {

    private SearchHitMapper() {
    }

    @Data
    public static class Hit {
        private Long kbId;
        private Long chunkId;
        private Long documentId;
        private String title;
        private String docType;
        private String docNo;
        private String section;
        private String content;
        private double score;
    }

    public static SearchHitVO toVo(Hit hit) {
        SearchHitVO vo = new SearchHitVO();
        vo.setChunkId(hit.getChunkId());
        vo.setDocumentId(hit.getDocumentId());
        vo.setDocumentTitle(hit.getTitle());
        vo.setDocType(hit.getDocType());
        vo.setDocNo(hit.getDocNo());
        vo.setSection(hit.getSection());
        vo.setContent(hit.getContent());
        vo.setScore(hit.getScore());
        return vo;
    }

    public static SearchHitVO fromMatch(EmbeddingMatch<TextSegment> match, Long kbId) {
        return toVo(KbEmbeddingStore.fromMatch(match, kbId));
    }
}
