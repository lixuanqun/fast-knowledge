package com.fast.knowledge.service;

import com.fast.knowledge.model.entity.KbDocument;

import java.time.LocalDate;

/**
 * 文档是否允许进入检索 / RAG 召回。
 * <ul>
 *   <li>{@code enabled != 1} — 禁用（作废 / 不参与检索）</li>
 *   <li>{@code effectiveDate > today} — 尚未生效</li>
 *   <li>{@code expireDate < today} — 已失效（失效当日仍可召回）</li>
 * </ul>
 */
public final class DocumentRecallPolicy {

    private DocumentRecallPolicy() {
    }

    public static boolean isRecallable(KbDocument doc) {
        return isRecallable(doc, LocalDate.now());
    }

    public static boolean isRecallable(KbDocument doc, LocalDate today) {
        if (doc == null) {
            return false;
        }
        if (doc.getEnabled() != null && doc.getEnabled() != 1) {
            return false;
        }
        if (doc.getEffectiveDate() != null && doc.getEffectiveDate().isAfter(today)) {
            return false;
        }
        if (doc.getExpireDate() != null && doc.getExpireDate().isBefore(today)) {
            return false;
        }
        return true;
    }

    /**
     * 向量侧过取倍数：过滤生命周期后尽量仍能凑满 topK。
     */
    public static int overFetch(int baseFetch) {
        if (baseFetch <= 0) {
            return 10;
        }
        return Math.min(Math.max(baseFetch * 3, baseFetch + 10), 100);
    }
}
