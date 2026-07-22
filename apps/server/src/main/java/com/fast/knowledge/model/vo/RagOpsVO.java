package com.fast.knowledge.model.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class RagOpsVO {
    private long searchCount;
    private long ragCount;
    private long zeroHitCount;
    private double zeroHitRate;
    private double cacheHitRate;
    private Double searchLatencyMeanMs;
    private Double searchLatencyP95Ms;
    private Double ragLatencyMeanMs;
    private Double ragLatencyP95Ms;
    private List<QueryStat> hotQueries = new ArrayList<>();
    private List<ZeroQuery> recentZeroQueries = new ArrayList<>();
    private long qaHistoryCount;
    private long agenticCount;

    @Data
    public static class QueryStat {
        private String query;
        private long count;
    }

    @Data
    public static class ZeroQuery {
        private Long kbId;
        private String query;
        private LocalDateTime createdAt;
    }
}
