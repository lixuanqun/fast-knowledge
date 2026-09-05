package com.fast.knowledge.common;

/**
 * 通用字符串工具方法。
 */
public final class StringUtils {

    private StringUtils() {
        // 工具类不实例化
    }

    /**
     * 截断字符串至指定长度，超出部分丢弃。
     *
     * @param value 原始字符串，可为 null
     * @param max   最大长度
     * @return 截断后的字符串，null 输入返回 null
     */
    public static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /**
     * 截断字符串至指定长度，超出部分以 "..." 替换。
     *
     * @param value 原始字符串，不可为 null
     * @param max   最大长度
     * @return 截断后的字符串
     */
    public static String truncateEllipsis(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }

    /**
     * 检索命中去重键（WIKI 类按 wiki:docId，文档类按 chunkId）。
     */
    public static String dedupeKey(String docType, Long documentId, Long chunkId) {
        if ("WIKI".equals(docType)) {
            return "wiki:" + documentId;
        }
        return "doc:" + documentId + ":" + chunkId;
    }

    /**
     * 构建文档来源提示字符串，用于 LLM 提示词。
     */
    public static String buildSourceHint(String title, String docNo, String docType, String department) {
        StringBuilder sb = new StringBuilder("文档：《").append(title).append("》");
        if (docNo != null && !docNo.isBlank()) {
            sb.append("，文号：").append(docNo);
        }
        if (docType != null && !docType.isBlank()) {
            sb.append("，类型：").append(docType);
        }
        if (department != null && !department.isBlank()) {
            sb.append("，部门：").append(department);
        }
        return sb.toString();
    }
}
