package com.fast.knowledge.model.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class ScenarioTemplateVO {
    private String id;
    private String name;
    private String category;
    private String description;
    private String suggestedKbName;
    private Map<String, Object> recommendedChunk = new LinkedHashMap<>();
    private List<String> sampleDocTypes = new ArrayList<>();
    private Map<String, String> docTypeLabels = new LinkedHashMap<>();
    private List<String> checklist = new ArrayList<>();
}
