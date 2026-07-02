package com.fast.knowledge.vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HybridSearchMerger {

    private HybridSearchMerger() {
    }

    public static List<SearchHit> merge(List<SearchHit> vectorHits, List<SearchHit> textHits,
                                        double alpha, int topK) {
        Map<Long, Double> scores = new HashMap<>();
        Map<Long, SearchHit> chunks = new HashMap<>();

        for (SearchHit hit : vectorHits) {
            scores.merge(hit.getChunkId(), hit.getScore() * alpha, Double::sum);
            chunks.put(hit.getChunkId(), hit);
        }
        for (SearchHit hit : textHits) {
            scores.merge(hit.getChunkId(), hit.getScore() * (1 - alpha), Double::sum);
            chunks.put(hit.getChunkId(), hit);
        }

        List<SearchHit> result = new ArrayList<>();
        scores.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(topK)
                .forEach(entry -> {
                    SearchHit hit = chunks.get(entry.getKey());
                    hit.setScore(entry.getValue());
                    result.add(hit);
                });
        return result;
    }
}
