package com.fast.knowledge.langchain4j;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * LangChain4j AiServices 声明式助手（用于简单问答场景）。
 */
public interface KnowledgeAssistant {

    @SystemMessage("你是 Fast Knowledge 快速知识库助手。请仅根据用户提供的参考资料回答问题。"
            + "若参考资料不足以回答，请明确说明「知识库中未找到相关内容」，不要编造。"
            + "回答请使用简体中文，条理清晰。")
    String answer(@UserMessage String userMessage);
}
