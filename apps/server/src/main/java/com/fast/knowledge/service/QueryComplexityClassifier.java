package com.fast.knowledge.service;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 查询复杂度启发式分类：SIMPLE 单次 HYBRID；COMPLEX 走有限多跳。
 */
public final class QueryComplexityClassifier {

    public enum Complexity {
        SIMPLE,
        COMPLEX
    }

    private static final Pattern COMPLEX_PATTERN = Pattern.compile(
            "(对比|比较|区别|差异|不同|相同|分别|各自|以及|同时|还有|除此之外|"
                    + "为什么.+以及|如何.+并且|哪些.+哪些|与.+相比|"
                    + "优缺点|利弊|前后|之前.+之后)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern MULTI_CLAUSE = Pattern.compile("[？?].+[？?]|[，,；;].{6,}[，,；;]");

    private QueryComplexityClassifier() {
    }

    public static Complexity classify(String query) {
        if (query == null || query.isBlank()) {
            return Complexity.SIMPLE;
        }
        String q = query.trim();
        if (q.length() <= 12) {
            return Complexity.SIMPLE;
        }
        String lower = q.toLowerCase(Locale.ROOT);
        if (COMPLEX_PATTERN.matcher(lower).find()) {
            return Complexity.COMPLEX;
        }
        if (q.length() >= 36 && MULTI_CLAUSE.matcher(q).find()) {
            return Complexity.COMPLEX;
        }
        // 两个及以上「和/与」连接的实体对比倾向
        int connectors = count(lower, "和") + count(lower, "与") + count(lower, "以及");
        if (connectors >= 2 && q.length() >= 20) {
            return Complexity.COMPLEX;
        }
        return Complexity.SIMPLE;
    }

    public static boolean isComplex(String query) {
        return classify(query) == Complexity.COMPLEX;
    }

    private static int count(String text, String token) {
        int n = 0;
        int idx = 0;
        while ((idx = text.indexOf(token, idx)) >= 0) {
            n++;
            idx += token.length();
        }
        return n;
    }
}
