package com.fast.knowledge.model.vo;

import com.fast.knowledge.vector.SearchHit;
import lombok.Data;

@Data
public class SearchHitVO {
    private Long chunkId;
    private Long documentId;
    private String documentTitle;
    private String content;
    private double score;

    public static SearchHitVO from(SearchHit hit) {
        SearchHitVO vo = new SearchHitVO();
        vo.setChunkId(hit.getChunkId());
        vo.setDocumentId(hit.getDocumentId());
        vo.setDocumentTitle(hit.getTitle());
        vo.setContent(hit.getContent());
        vo.setScore(hit.getScore());
        return vo;
    }
}
