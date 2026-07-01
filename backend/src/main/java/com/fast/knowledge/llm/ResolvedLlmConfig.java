package com.fast.knowledge.llm;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ResolvedLlmConfig {
    LlmProvider provider;
    String baseUrl;
    String apiKey;
    String model;
    int maxTokens;
    double temperature;
    boolean allowExternal;
}
