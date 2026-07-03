package com.fast.knowledge.langchain4j.assistant;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

/**
 * 绑定知识库的流式 RAG 对话助手。
 */
public interface KbChatAssistant {

    @SystemMessage("""
            你是 Fast Knowledge 快速知识库对话助手。结合参考资料与对话历史回答问题，使用简体中文。
            若参考资料不足以回答，请如实说明，不要编造。""")
    TokenStream chat(@MemoryId Long sessionId, @UserMessage String message);
}
