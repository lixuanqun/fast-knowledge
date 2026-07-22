package com.fast.knowledge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fast.knowledge.model.vo.ScenarioTemplateVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
public class ScenarioTemplateService {

    private final ObjectMapper objectMapper;
    private volatile List<ScenarioTemplateVO> cache;

    public ScenarioTemplateService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<ScenarioTemplateVO> listAll() {
        if (cache == null) {
            synchronized (this) {
                if (cache == null) {
                    cache = load();
                }
            }
        }
        return cache;
    }

    public ScenarioTemplateVO getById(String id) {
        return listAll().stream()
                .filter(t -> t.getId() != null && t.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    private List<ScenarioTemplateVO> load() {
        List<ScenarioTemplateVO> list = new ArrayList<>();
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath*:scenarios/*.json");
            for (Resource resource : resources) {
                try (InputStream in = resource.getInputStream()) {
                    ScenarioTemplateVO vo = objectMapper.readValue(in, ScenarioTemplateVO.class);
                    if (vo.getId() != null) {
                        list.add(vo);
                    }
                } catch (Exception e) {
                    log.warn("Failed to load scenario {}: {}", resource.getFilename(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to scan scenario templates: {}", e.getMessage());
        }
        list.sort(Comparator.comparing(ScenarioTemplateVO::getId, Comparator.nullsLast(String::compareTo)));
        return List.copyOf(list);
    }
}
