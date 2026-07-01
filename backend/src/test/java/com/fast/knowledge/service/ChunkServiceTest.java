package com.fast.knowledge.service;

import com.fast.knowledge.config.KnowledgeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkServiceTest {

    private ChunkService chunkService;

    @BeforeEach
    void setUp() {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.getChunk().setSize(100);
        properties.getChunk().setOverlap(20);
        chunkService = new ChunkService(properties);
    }

    @Test
    void splitsByParagraphFirst() {
        String p1 = "第一段".repeat(30);
        String p2 = "第二段".repeat(30);
        String text = p1 + "\n\n" + p2;
        List<String> chunks = chunkService.split(text);
        assertTrue(chunks.size() >= 2);
    }

    @Test
    void countsTokens() {
        assertTrue(chunkService.countTokens("hello world") > 0);
        assertEquals(0, chunkService.countTokens("   "));
    }

    private static void assertEquals(int expected, int actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
