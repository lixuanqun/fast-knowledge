package com.fast.knowledge.storage;

/**
 * 已持久化的文件引用。
 */
public record StoredObject(String absolutePath, String extension, long size) {
}
