package com.fast.knowledge.langchain4j.assistant;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * LangChain4j AiServices 声明式知识库问答（RAG 由 contentRetriever 注入上下文）。
 */
public interface KbAssistant {

    @SystemMessage("""
            你是 Fast Knowledge 快速知识库助手。请仅根据提供的参考资料回答问题。
            若参考资料不足以回答，请明确说明「知识库中未找到相关内容」，不要编造。
            回答请使用简体中文，条理清晰。""")
    String answer(@UserMessage String question);
}
