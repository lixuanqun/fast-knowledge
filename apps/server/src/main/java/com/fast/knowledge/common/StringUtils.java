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
}
