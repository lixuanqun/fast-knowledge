package com.fast.knowledge.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fast.knowledge.mapper.DocumentChunkMapper;
import com.fast.knowledge.mapper.DocumentMapper;
import com.fast.knowledge.mapper.KbGapQueryMapper;
import com.fast.knowledge.model.entity.DocumentChunk;
import com.fast.knowledge.model.entity.KbDocument;
import com.fast.knowledge.model.entity.KbGapQuery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WP9 主动知识运营 — 知识缺口 / 过期提醒 / 冲突检测。
 *
 * <p>缺口采集：检索命中数 ≤ 阈值时记入 kb_gap_query（同 query 增量计数），
 * 运营界面按频次排序展示高频无结果查询，指导补文档；
 * 过期扫描：kb_document.expire_date 已过或 effective_date 尚未生效的文档；
 * 冲突检测：新文档入库与既有 chunk 内容高相似（简版：文号/标题完全相同）的候选列表。
 */
@Slf4j
@Service
public class KnowledgeOpsService {

    /**
     * 缺口判据：hits 为 0（向量检索未返回结果，真无相关内容）。
     * 不依赖 score 阈值——不同 embedding 模型的分数分布差异巨大（云端模型普遍 >0.6），
     * 纯靠向量分数判缺口不可靠。后续可引入 LLM 自评（WP6 Sufficiency）做语义缺口判据。
     */
    private static final int LOW_HIT_THRESHOLD = 1;
    private static final double LOW_SCORE_THRESHOLD = 1.0; // 等价禁用 score 判据
    private static final int GAP_QUERY_LIMIT = 50;

    private final KbGapQueryMapper gapQueryMapper;
    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;

    public KnowledgeOpsService(KbGapQueryMapper gapQueryMapper, DocumentMapper documentMapper,
                                DocumentChunkMapper documentChunkMapper) {
        this.gapQueryMapper = gapQueryMapper;
        this.documentMapper = documentMapper;
        this.documentChunkMapper = documentChunkMapper;
    }

    // ---- 缺口采集 ----

    /** 检索低命中时调用；同 query 增量计数，无则插入 */
    public void recordGap(Long kbId, String query, int hitCount, double topScore) {
        // hits 太少 或 最高分太低 → 视为知识缺口
        if (hitCount >= LOW_HIT_THRESHOLD && topScore >= LOW_SCORE_THRESHOLD) {
            return;
        }
        try {
            KbGapQuery existing = gapQueryMapper.selectOne(Wrappers.<KbGapQuery>lambdaQuery()
                    .eq(KbGapQuery::getKbId, kbId)
                    .eq(KbGapQuery::getQuery, query)
                    .eq(KbGapQuery::getStatus, "OPEN")
                    .last("LIMIT 1"));
            if (existing != null) {
                existing.setHitCount(existing.getHitCount() + 1);
                gapQueryMapper.updateById(existing);
            } else {
                KbGapQuery gap = new KbGapQuery();
                gap.setKbId(kbId);
                gap.setQuery(query);
                gap.setHitCount(1);
                gap.setStatus("OPEN");
                gapQueryMapper.insert(gap);
            }
        } catch (Exception e) {
            // 采集失败不阻塞检索主流程
            log.debug("缺口采集失败: {}", e.getMessage());
        }
    }

    /** 高频无结果查询 Top N（按 hitCount 降序） */
    public List<KbGapQuery> listGaps(Long kbId, int limit) {
        return gapQueryMapper.selectList(Wrappers.<KbGapQuery>lambdaQuery()
                .eq(kbId != null, KbGapQuery::getKbId, kbId)
                .eq(KbGapQuery::getStatus, "OPEN")
                .orderByDesc(KbGapQuery::getHitCount)
                .last("LIMIT " + Math.min(Math.max(limit, 1), GAP_QUERY_LIMIT)));
    }

    public void resolveGap(Long id, String status) {
        KbGapQuery gap = gapQueryMapper.selectById(id);
        if (gap != null) {
            gap.setStatus(status == null ? "ADDRESSED" : status);
            gapQueryMapper.updateById(gap);
        }
    }

    // ---- 过期/未生效扫描 ----

    public List<KbDocument> listExpiredDocs(Long kbId) {
        LocalDate today = LocalDate.now();
        return documentMapper.selectList(Wrappers.<KbDocument>lambdaQuery()
                .eq(kbId != null, KbDocument::getKbId, kbId)
                .eq(KbDocument::getEnabled, 1)
                .and(w -> w
                        .isNotNull(KbDocument::getExpireDate).lt(KbDocument::getExpireDate, today)
                        .or()
                        .isNotNull(KbDocument::getEffectiveDate).gt(KbDocument::getEffectiveDate, today))
                .orderByDesc(KbDocument::getExpireDate));
    }

    // ---- 冲突检测（简版：同 KB 内文号/标题完全相同的文档对） ----

    public List<Map<String, Object>> listConflicts(Long kbId) {
        List<KbDocument> docs = documentMapper.selectList(Wrappers.<KbDocument>lambdaQuery()
                .eq(kbId != null, KbDocument::getKbId, kbId)
                .eq(KbDocument::getEnabled, 1)
                .isNotNull(KbDocument::getDocNo));
        Map<String, List<KbDocument>> byDocNo = new LinkedHashMap<>();
        Map<String, List<KbDocument>> byTitle = new LinkedHashMap<>();
        for (KbDocument d : docs) {
            if (d.getDocNo() != null && !d.getDocNo().isBlank()) {
                byDocNo.computeIfAbsent(d.getDocNo().trim(), k -> new ArrayList<>()).add(d);
            }
            if (d.getTitle() != null && !d.getTitle().isBlank()) {
                byTitle.computeIfAbsent(d.getTitle().trim(), k -> new ArrayList<>()).add(d);
            }
        }
        List<Map<String, Object>> conflicts = new ArrayList<>();
        for (var entry : byDocNo.entrySet()) {
            if (entry.getValue().size() > 1) {
                conflicts.add(Map.of("type", "文号重复", "key", entry.getKey(),
                        "docs", entry.getValue().stream().map(KbDocument::getId).toList()));
            }
        }
        for (var entry : byTitle.entrySet()) {
            if (entry.getValue().size() > 1) {
                conflicts.add(Map.of("type", "标题重复", "key", entry.getKey(),
                        "docs", entry.getValue().stream().map(KbDocument::getId).toList()));
            }
        }
        return conflicts;
    }
}
