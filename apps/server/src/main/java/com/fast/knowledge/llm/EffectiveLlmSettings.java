package com.fast.knowledge.llm;

import lombok.Builder;
import lombok.Value;

/**
 * 合并数据库与环境变量后的 LLM 配置（尚未应用预设默认值）。
 */
@Value
@Builder
public class EffectiveLlmSettings {
    String provider;
    String baseUrl;
    String apiKey;
    String model;
    boolean allowExternal;
    int maxTokens;
    double temperature;
}
