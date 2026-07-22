package com.fast.knowledge.config;

import com.fast.knowledge.common.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditionGuardTest {

    @Test
    void communityBlocksEnterpriseFeatures() {
        KnowledgeProperties props = new KnowledgeProperties();
        props.setEdition("community");
        EditionGuard guard = new EditionGuard(props);
        BusinessException ex = assertThrows(BusinessException.class, () -> guard.requireEnterprise("API Key"));
        assertEquals(403, ex.getCode());
        assertTrue(ex.getMessage().contains("企业版"));
    }

    @Test
    void enterpriseAllows() {
        KnowledgeProperties props = new KnowledgeProperties();
        props.setEdition("enterprise");
        EditionGuard guard = new EditionGuard(props);
        assertDoesNotThrow(() -> guard.requireEnterprise("API Key"));
        assertTrue(guard.isEnterprise());
    }
}
