package com.fast.knowledge.langchain4j.assistant;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

/**
 * 无知识库绑定的流式对话助手。
 */
public interface GeneralChatAssistant {

    @SystemMessage("""
            你是 Fast Knowledge 快速知识库对话助手。结合对话历史回答问题，使用简体中文。
            若问题超出一般知识范围，请如实说明。""")
    TokenStream chat(@MemoryId Long sessionId, @UserMessage String message);
}
