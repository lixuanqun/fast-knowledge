package com.fast.knowledge.security;

import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.config.KnowledgeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalAccessGuardTest {

    private KnowledgeProperties properties;
    private ExternalAccessGuard guard;

    @BeforeEach
    void setUp() {
        properties = new KnowledgeProperties();
        guard = new ExternalAccessGuard(properties);
    }

    @Test
    void allowsLocalOllamaWhenExternalDisabled() {
        properties.getLlm().setAllowExternal(false);
        assertDoesNotThrow(() -> guard.validateEmbeddingEndpoint("http://localhost:11434"));
        assertDoesNotThrow(() -> guard.validateEmbeddingEndpoint("http://ollama:11434/v1"));
    }

    @Test
    void blocksRemoteEmbeddingWhenExternalDisabled() {
        properties.getLlm().setAllowExternal(false);
        assertThrows(BusinessException.class,
                () -> guard.validateEmbeddingEndpoint("https://api.example.com"));
    }

    @Test
    void allowsRemoteWhenExternalEnabled() {
        properties.getLlm().setAllowExternal(true);
        assertDoesNotThrow(() -> guard.validateLlmEndpoint("https://api.deepseek.com/v1"));
    }

    @Test
    void detectsDockerInternalHostAsLocal() {
        assertTrue(guard.isLocalUrl("http://host.docker.internal:11434"));
    }
}
