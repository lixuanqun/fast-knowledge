package com.fast.knowledge.llm;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

import java.util.List;

public class DelegatingStreamingChatModel implements StreamingChatModel {

    private final LlmModelRegistry registry;

    public DelegatingStreamingChatModel(LlmModelRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void chat(List<ChatMessage> messages, StreamingChatResponseHandler handler) {
        registry.getStreamingChatModel().chat(messages, handler);
    }
}
