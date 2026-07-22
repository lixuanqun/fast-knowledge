package com.fast.knowledge.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryComplexityClassifierTest {

    @Test
    void simpleFactualQueries() {
        assertEquals(QueryComplexityClassifier.Complexity.SIMPLE,
                QueryComplexityClassifier.classify("轴承型号"));
        assertEquals(QueryComplexityClassifier.Complexity.SIMPLE,
                QueryComplexityClassifier.classify("安全帽佩戴要求是什么"));
        assertFalse(QueryComplexityClassifier.isComplex("电机功率"));
    }

    @Test
    void complexComparisonQueries() {
        assertTrue(QueryComplexityClassifier.isComplex("工艺A与工艺B的区别是什么"));
        assertTrue(QueryComplexityClassifier.isComplex("对比新旧制度的差异以及适用范围"));
        assertTrue(QueryComplexityClassifier.isComplex("分别说明设备维保周期与点检项目"));
    }

    @Test
    void heuristicSplit() {
        List<String> parts = AgenticRetrievalService.heuristicSplit("工艺A与工艺B的区别");
        assertFalse(parts.isEmpty());
    }
}
