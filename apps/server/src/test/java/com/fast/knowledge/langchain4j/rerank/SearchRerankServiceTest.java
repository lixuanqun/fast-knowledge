package com.fast.knowledge.langchain4j.rerank;

import com.fast.knowledge.model.vo.SearchHitVO;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchRerankServiceTest {

    @Test
    void rerank_ordersByScoreAndLimitsTopK() {
        KnowledgePropertiesStub properties = new KnowledgePropertiesStub();
        properties.getSearch().getRerank().setEnabled(true);
        properties.getSearch().getRerank().setCandidateMultiplier(3);

        ScoringModel scoringModel = (segments, query) -> Response.from(segments.stream()
                .map(segment -> segment.text().contains("content A") ? 0.9 : 0.1)
                .toList());

        SearchRerankService service = new SearchRerankService(
                java.util.Optional.of(scoringModel), properties);

        SearchHitVO a = hit("Doc A", "content A");
        SearchHitVO b = hit("Doc B", "content B");
        List<SearchHitVO> reranked = service.rerank("query", List.of(b, a), 1);

        assertEquals(1, reranked.size());
        assertEquals("Doc A", reranked.getFirst().getDocumentTitle());
        assertEquals(0.9, reranked.getFirst().getScore(), 0.001);
    }

    private static SearchHitVO hit(String title, String content) {
        SearchHitVO vo = new SearchHitVO();
        vo.setDocumentTitle(title);
        vo.setContent(content);
        vo.setScore(0.5);
        return vo;
    }

    /** 测试用，避免构造完整 KnowledgeProperties 图。 */
    private static final class KnowledgePropertiesStub extends com.fast.knowledge.config.KnowledgeProperties {
    }
}
