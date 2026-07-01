package com.fast.knowledge.embedding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OllamaUrlHelperTest {

    @Test
    void stripsV1Suffix() {
        assertEquals("http://ollama:11434", OllamaUrlHelper.toNativeBaseUrl("http://ollama:11434/v1"));
    }

    @Test
    void stripsTrailingSlashAndV1() {
        assertEquals("http://localhost:11434", OllamaUrlHelper.toNativeBaseUrl("http://localhost:11434/v1/"));
    }

    @Test
    void keepsNativeUrl() {
        assertEquals("http://localhost:11434", OllamaUrlHelper.toNativeBaseUrl("http://localhost:11434"));
    }

    @Test
    void defaultsWhenBlank() {
        assertEquals("http://localhost:11434", OllamaUrlHelper.toNativeBaseUrl("  "));
    }
}
