package com.fast.knowledge.langchain4j.rerank;

import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.model.vo.SearchHitVO;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.scoring.ScoringModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class SearchRerankService {

    private final Optional<ScoringModel> scoringModel;
    private final KnowledgeProperties.Rerank rerankConfig;

    public SearchRerankService(Optional<ScoringModel> scoringModel, KnowledgeProperties properties) {
        this.scoringModel = scoringModel;
        this.rerankConfig = properties.getSearch().getRerank();
    }

    public boolean isActive() {
        return rerankConfig.isEnabled() && scoringModel.isPresent();
    }

    public int candidateCount(int topK) {
        int multiplier = Math.max(1, rerankConfig.getCandidateMultiplier());
        return Math.max(topK, topK * multiplier);
    }

    public List<SearchHitVO> rerank(String query, List<SearchHitVO> hits, int topK) {
        if (!isActive() || hits.isEmpty()) {
            return hits.stream().limit(topK).toList();
        }
        List<TextSegment> segments = hits.stream()
                .map(hit -> TextSegment.from(formatForScoring(hit)))
                .toList();
        List<Double> scores = scoringModel.get().scoreAll(segments, query).content();

        List<ScoredHit> scored = new ArrayList<>(hits.size());
        for (int i = 0; i < hits.size(); i++) {
            scored.add(new ScoredHit(hits.get(i), scores.get(i)));
        }
        scored.sort(Comparator.comparingDouble(ScoredHit::score).reversed());

        Double minScore = rerankConfig.getMinScore();
        List<SearchHitVO> result = new ArrayList<>();
        for (ScoredHit item : scored) {
            if (minScore != null && item.score() < minScore) {
                continue;
            }
            SearchHitVO vo = item.hit();
            vo.setScore(item.score());
            result.add(vo);
            if (result.size() >= topK) {
                break;
            }
        }
        return result;
    }

    private static String formatForScoring(SearchHitVO hit) {
        String title = hit.getDocumentTitle() != null ? hit.getDocumentTitle() : "";
        return "【" + title + "】\n" + hit.getContent();
    }

    private record ScoredHit(SearchHitVO hit, double score) {
    }
}
