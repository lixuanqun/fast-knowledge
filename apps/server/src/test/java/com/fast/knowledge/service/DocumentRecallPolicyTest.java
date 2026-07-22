package com.fast.knowledge.service;

import com.fast.knowledge.model.entity.KbDocument;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentRecallPolicyTest {

    private final LocalDate today = LocalDate.of(2026, 7, 22);

    @Test
    void allowsActiveDocumentWithoutDates() {
        KbDocument doc = doc(1, null, null);
        assertTrue(DocumentRecallPolicy.isRecallable(doc, today));
    }

    @Test
    void excludesDisabled() {
        KbDocument doc = doc(0, null, null);
        assertFalse(DocumentRecallPolicy.isRecallable(doc, today));
    }

    @Test
    void excludesNotYetEffective() {
        KbDocument doc = doc(1, today.plusDays(1), null);
        assertFalse(DocumentRecallPolicy.isRecallable(doc, today));
    }

    @Test
    void includesEffectiveToday() {
        KbDocument doc = doc(1, today, null);
        assertTrue(DocumentRecallPolicy.isRecallable(doc, today));
    }

    @Test
    void excludesExpiredYesterday() {
        KbDocument doc = doc(1, null, today.minusDays(1));
        assertFalse(DocumentRecallPolicy.isRecallable(doc, today));
    }

    @Test
    void includesExpireTodayInclusive() {
        KbDocument doc = doc(1, null, today);
        assertTrue(DocumentRecallPolicy.isRecallable(doc, today));
    }

    @Test
    void nullDocumentNotRecallable() {
        assertFalse(DocumentRecallPolicy.isRecallable(null, today));
    }

    @Test
    void overFetchCapsAt100() {
        assertEquals(100, DocumentRecallPolicy.overFetch(50));
        assertTrue(DocumentRecallPolicy.overFetch(5) >= 15);
    }

    private static KbDocument doc(Integer enabled, LocalDate effective, LocalDate expire) {
        KbDocument d = new KbDocument();
        d.setEnabled(enabled);
        d.setEffectiveDate(effective);
        d.setExpireDate(expire);
        return d;
    }
}
