package com.fast.knowledge.langchain4j.adapter;

import com.fast.knowledge.ai.port.VisionPort;
import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.llm.LlmConfigResolver;
import com.fast.knowledge.llm.ResolvedLlmConfig;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * 视觉问答适配器 — 每次请求按当前 LLM 配置构建视觉模型实例（qwen-vl 系列），
 * 支持管理端热刷新；图片以 base64 data URL 走 OpenAI 兼容多模态格式。
 */
@Slf4j
@Service
public class LangChain4jVisionAdapter implements VisionPort {

    private final LlmConfigResolver llmConfigResolver;
    private final KnowledgeProperties properties;

    public LangChain4jVisionAdapter(LlmConfigResolver llmConfigResolver, KnowledgeProperties properties) {
        this.llmConfigResolver = llmConfigResolver;
        this.properties = properties;
    }

    @Override
    public String askAboutImage(String imageBase64, String mimeType, String question) {
        ResolvedLlmConfig cfg = llmConfigResolver.resolve();
        ChatModel model = OpenAiChatModel.builder()
                .baseUrl(cfg.getBaseUrl())
                .apiKey(cfg.getApiKey())
                .modelName(properties.getVision().getModel())
                .temperature(cfg.getTemperature())
                .maxTokens(cfg.getMaxTokens())
                .timeout(Duration.ofSeconds(90))
                .build();
        UserMessage message = UserMessage.from(List.of(
                ImageContent.from(imageBase64, mimeType),
                TextContent.from(question)));
        return model.chat(List.of(message)).aiMessage().text();
    }
}
