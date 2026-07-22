package com.fast.knowledge.service;

import com.fast.knowledge.mapper.DocumentMapper;
import com.fast.knowledge.model.entity.KbDocument;
import com.fast.knowledge.model.vo.SearchHitVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentLifecycleFilterTest {

    @Mock
    private DocumentMapper documentMapper;

    @InjectMocks
    private DocumentLifecycleFilter filter;

    @Test
    void dropsExpiredAndDisabledHits() {
        LocalDate today = LocalDate.of(2026, 7, 22);
        KbDocument active = doc(1L, 1, null, null);
        KbDocument expired = doc(2L, 1, null, today.minusDays(1));
        KbDocument disabled = doc(3L, 0, null, null);
        when(documentMapper.selectByIds(anyCollection())).thenReturn(List.of(active, expired, disabled));

        List<SearchHitVO> hits = List.of(hit(1L), hit(2L), hit(3L), hit(1L));
        List<SearchHitVO> filtered = filter.filter(hits, today);

        assertEquals(2, filtered.size());
        assertEquals(1L, filtered.get(0).getDocumentId());
        assertEquals(1L, filtered.get(1).getDocumentId());
    }

    private static KbDocument doc(Long id, Integer enabled, LocalDate effective, LocalDate expire) {
        KbDocument d = new KbDocument();
        d.setId(id);
        d.setEnabled(enabled);
        d.setEffectiveDate(effective);
        d.setExpireDate(expire);
        return d;
    }

    private static SearchHitVO hit(Long docId) {
        SearchHitVO vo = new SearchHitVO();
        vo.setDocumentId(docId);
        vo.setContent("x");
        return vo;
    }
}
