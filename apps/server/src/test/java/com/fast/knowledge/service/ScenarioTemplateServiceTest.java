package com.fast.knowledge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fast.knowledge.model.vo.ScenarioTemplateVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioTemplateServiceTest {

    @Test
    void loadsClasspathScenarios() {
        ScenarioTemplateService service = new ScenarioTemplateService(new ObjectMapper());
        List<ScenarioTemplateVO> list = service.listAll();
        assertFalse(list.isEmpty());
        assertTrue(list.stream().anyMatch(s -> "manufacturing-process".equals(s.getId())));
        ScenarioTemplateVO one = service.getById("manufacturing-policy");
        assertNotNull(one);
        assertNotNull(one.getRecommendedChunk());
    }
}
