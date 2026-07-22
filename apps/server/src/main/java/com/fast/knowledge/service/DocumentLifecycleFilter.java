package com.fast.knowledge.service;

import com.fast.knowledge.mapper.DocumentMapper;
import com.fast.knowledge.model.entity.KbDocument;
import com.fast.knowledge.model.vo.SearchHitVO;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 召回后按文档生命周期过滤 hits（禁用 / 未生效 / 已过期）。
 */
@Component
public class DocumentLifecycleFilter {

    private final DocumentMapper documentMapper;

    public DocumentLifecycleFilter(DocumentMapper documentMapper) {
        this.documentMapper = documentMapper;
    }

    public List<SearchHitVO> filter(List<SearchHitVO> hits) {
        return filter(hits, LocalDate.now());
    }

    public List<SearchHitVO> filter(List<SearchHitVO> hits, LocalDate today) {
        if (hits == null || hits.isEmpty()) {
            return hits == null ? List.of() : hits;
        }
        Set<Long> docIds = hits.stream()
                .map(SearchHitVO::getDocumentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (docIds.isEmpty()) {
            return List.of();
        }
        List<KbDocument> docs = documentMapper.selectByIds(docIds);
        Map<Long, KbDocument> byId = docs == null
                ? Collections.emptyMap()
                : docs.stream().collect(Collectors.toMap(KbDocument::getId, Function.identity(), (a, b) -> a));

        return hits.stream()
                .filter(hit -> {
                    Long docId = hit.getDocumentId();
                    if (docId == null) {
                        return false;
                    }
                    return DocumentRecallPolicy.isRecallable(byId.get(docId), today);
                })
                .toList();
    }
}
