package com.fast.knowledge.llm;

import lombok.Getter;

/**
 * 模型中立：预设主流 OpenAI 兼容 API 提供商。
 * 设置 knowledge.llm.provider 后自动填充 baseUrl / 默认 model，仍可用环境变量覆盖。
 */
@Getter
public enum LlmProvider {

    OLLAMA("ollama", "本地 Ollama", "http://localhost:11434/v1", "ollama", "qwen2.5:7b", true),
    DEEPSEEK("deepseek", "DeepSeek", "https://api.deepseek.com/v1", "", "deepseek-chat", false),
    GLM("glm", "智谱 GLM", "https://open.bigmodel.cn/api/paas/v4", "", "glm-4-flash", false),
    DASHSCOPE("dashscope", "阿里云百炼", "https://dashscope.aliyuncs.com/compatible-mode/v1", "", "qwen-plus", false),
    VOLCENGINE("volcengine", "火山引擎方舟", "https://ark.cn-beijing.volces.com/api/v3", "", "", false),
    OPENAI("openai", "OpenAI", "https://api.openai.com/v1", "", "gpt-4o-mini", false),
    CUSTOM("custom", "自定义", "", "", "", false);

    private final String id;
    private final String displayName;
    private final String defaultBaseUrl;
    private final String defaultApiKeyPlaceholder;
    private final String defaultModel;
    private final boolean local;

    LlmProvider(String id, String displayName, String defaultBaseUrl,
                String defaultApiKeyPlaceholder, String defaultModel, boolean local) {
        this.id = id;
        this.displayName = displayName;
        this.defaultBaseUrl = defaultBaseUrl;
        this.defaultApiKeyPlaceholder = defaultApiKeyPlaceholder;
        this.defaultModel = defaultModel;
        this.local = local;
    }

    public static LlmProvider fromId(String id) {
        if (id == null || id.isBlank()) {
            return OLLAMA;
        }
        for (LlmProvider p : values()) {
            if (p.id.equalsIgnoreCase(id)) {
                return p;
            }
        }
        return CUSTOM;
    }
}
