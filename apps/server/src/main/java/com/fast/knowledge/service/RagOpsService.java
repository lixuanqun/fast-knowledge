package com.fast.knowledge.service;

import com.fast.knowledge.audit.AuditActions;
import com.fast.knowledge.mapper.AuditLogMapper;
import com.fast.knowledge.model.entity.AuditLog;
import com.fast.knowledge.model.vo.RagOpsVO;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RagOpsService {

    private static final Pattern QUERY_PATTERN = Pattern.compile("query=(.*?), hits=(\\d+)");
    private static final int AUDIT_SCAN_LIMIT = 500;
    private static final int HOT_TOP_N = 10;
    private static final int ZERO_TOP_N = 20;

    private final MeterRegistry meterRegistry;
    private final AuditLogMapper auditLogMapper;
    private final QaHistoryService qaHistoryService;

    public RagOpsService(MeterRegistry meterRegistry,
                         AuditLogMapper auditLogMapper,
                         QaHistoryService qaHistoryService) {
        this.meterRegistry = meterRegistry;
        this.auditLogMapper = auditLogMapper;
        this.qaHistoryService = qaHistoryService;
    }

    public RagOpsVO snapshot() {
        RagOpsVO vo = new RagOpsVO();

        long searchCount = counterValue("kb.search.count");
        long zeroHits = counterValue("kb.search.hits", "range", "0");
        long ragCount = counterValue("kb.rag.count");
        vo.setSearchCount(searchCount);
        vo.setRagCount(ragCount);
        vo.setZeroHitCount(zeroHits);
        vo.setZeroHitRate(searchCount > 0 ? (double) zeroHits / searchCount : 0.0);
        vo.setCacheHitRate(gaugeValue("kb.search.cache.hit_rate"));
        vo.setAgenticCount(sumCounter("kb.rag.agentic"));

        Timer searchTimer = meterRegistry.find("kb.search.latency").timer();
        if (searchTimer != null) {
            vo.setSearchLatencyMeanMs(searchTimer.mean(TimeUnit.MILLISECONDS));
            vo.setSearchLatencyP95Ms(percentileMs(searchTimer, 0.95));
        }
        Timer ragTimer = meterRegistry.find("kb.rag.latency").timer();
        if (ragTimer != null) {
            vo.setRagLatencyMeanMs(ragTimer.mean(TimeUnit.MILLISECONDS));
            vo.setRagLatencyP95Ms(percentileMs(ragTimer, 0.95));
        }

        List<AuditLog> recentSearch = auditLogMapper.findRecentByAction(AuditActions.SEARCH, AUDIT_SCAN_LIMIT);
        Map<String, Long> hot = new HashMap<>();
        List<RagOpsVO.ZeroQuery> zeros = new ArrayList<>();
        for (AuditLog log : recentSearch) {
            ParsedSearchDetail parsed = parse(log.getDetail());
            if (parsed == null || parsed.query().isBlank()) {
                continue;
            }
            hot.merge(parsed.query(), 1L, Long::sum);
            if (parsed.hits() == 0 && zeros.size() < ZERO_TOP_N) {
                RagOpsVO.ZeroQuery z = new RagOpsVO.ZeroQuery();
                z.setKbId(log.getTargetId());
                z.setQuery(parsed.query());
                z.setCreatedAt(log.getCreatedAt());
                zeros.add(z);
            }
        }
        vo.setHotQueries(hot.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(HOT_TOP_N)
                .map(e -> {
                    RagOpsVO.QueryStat s = new RagOpsVO.QueryStat();
                    s.setQuery(e.getKey());
                    s.setCount(e.getValue());
                    return s;
                })
                .toList());
        vo.setRecentZeroQueries(zeros);
        vo.setQaHistoryCount(qaHistoryService.countAll());
        return vo;
    }

    private long counterValue(String name) {
        Counter c = meterRegistry.find(name).counter();
        return c != null ? (long) c.count() : 0L;
    }

    private long counterValue(String name, String tagKey, String tagValue) {
        Counter c = meterRegistry.find(name).tag(tagKey, tagValue).counter();
        return c != null ? (long) c.count() : 0L;
    }

    private long sumCounter(String name) {
        return meterRegistry.find(name).counters().stream()
                .mapToLong(c -> (long) c.count())
                .sum();
    }

    private double gaugeValue(String name) {
        var g = meterRegistry.find(name).gauge();
        return g != null ? g.value() : 0.0;
    }

    private static Double percentileMs(Timer timer, double p) {
        try {
            double v = timer.percentile(p, TimeUnit.MILLISECONDS);
            return Double.isNaN(v) ? null : v;
        } catch (Exception e) {
            return null;
        }
    }

    static ParsedSearchDetail parse(String detail) {
        if (detail == null || detail.isBlank()) {
            return null;
        }
        Matcher m = QUERY_PATTERN.matcher(detail);
        if (!m.find()) {
            return null;
        }
        return new ParsedSearchDetail(m.group(1).trim(), Integer.parseInt(m.group(2)));
    }

    record ParsedSearchDetail(String query, int hits) {
    }
}
