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
            你是 Fast Knowledge 快速知识库对话助手。请优先且仅根据提供的参考资料回答问题，并在回答中标注来源文档。
            对话历史仅用于理解指代与上下文，不得作为事实依据。若参考资料不足以回答，请明确说明「知识库中未找到相关内容」，不要编造。
            回答请使用简体中文，条理清晰。""")
    TokenStream chat(@MemoryId Long sessionId, @UserMessage String message);
}
