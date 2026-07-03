package com.fast.knowledge.model.vo;

import lombok.Data;

@Data
public class LlmConfigVO {
    private String provider;
    private String providerName;
    private String baseUrl;
    private String model;
    private boolean allowExternal;
    private boolean configuredInDb;
    private boolean apiKeyConfigured;
    private String apiKeyMask;
}
