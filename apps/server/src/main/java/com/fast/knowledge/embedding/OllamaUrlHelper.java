package com.fast.knowledge.embedding;

/**
 * 将 OpenAI 兼容 baseUrl（含 /v1）规范为 Ollama 原生 API 根地址。
 */
final class OllamaUrlHelper {

    private OllamaUrlHelper() {
    }

    static String toNativeBaseUrl(String url) {
        if (url == null || url.isBlank()) {
            return "http://localhost:11434";
        }
        String normalized = url.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/v1")) {
            normalized = normalized.substring(0, normalized.length() - 3);
        }
        return normalized;
    }
}
