package com.fast.knowledge.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RagOpsServiceParseTest {

    @Test
    void parsesSearchAuditDetail() {
        var parsed = RagOpsService.parse("query=设备维保周期, hits=0, cache=miss");
        assertNotNull(parsed);
        assertEquals("设备维保周期", parsed.query());
        assertEquals(0, parsed.hits());
    }

    @Test
    void parsesCacheHitDetail() {
        var parsed = RagOpsService.parse("query=hello, hits=3, cache=hit");
        assertNotNull(parsed);
        assertEquals("hello", parsed.query());
        assertEquals(3, parsed.hits());
    }

    @Test
    void returnsNullForGarbage() {
        assertNull(RagOpsService.parse("nope"));
        assertNull(RagOpsService.parse(null));
    }
}
