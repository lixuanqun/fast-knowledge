package com.fast.knowledge.llm;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.List;

public class DelegatingChatModel implements ChatModel {

    private final LlmModelRegistry registry;

    public DelegatingChatModel(LlmModelRegistry registry) {
        this.registry = registry;
    }

    @Override
    public ChatResponse chat(ChatMessage... messages) {
        return registry.getChatModel().chat(messages);
    }

    @Override
    public ChatResponse chat(List<ChatMessage> messages) {
        return registry.getChatModel().chat(messages);
    }
}
