package com.fast.knowledge.llm;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

import java.util.List;

public class DelegatingStreamingChatModel implements StreamingChatModel {

    private final LlmModelRegistry registry;

    public DelegatingStreamingChatModel(LlmModelRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
        registry.getStreamingChatModel().doChat(request, handler);
    }

    @Override
    public void chat(ChatRequest request, StreamingChatResponseHandler handler) {
        registry.getStreamingChatModel().chat(request, handler);
    }

    @Override
    public void chat(List<ChatMessage> messages, StreamingChatResponseHandler handler) {
        registry.getStreamingChatModel().chat(messages, handler);
    }
}
