package com.fast.knowledge.langchain4j.adapter;

import com.fast.knowledge.ai.port.ChatPort;
import com.fast.knowledge.llm.LlmModelRegistry;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class LangChain4jChatAdapter implements ChatPort {

    private final LlmModelRegistry llmModelRegistry;

    public LangChain4jChatAdapter(LlmModelRegistry llmModelRegistry) {
        this.llmModelRegistry = llmModelRegistry;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            return llmModelRegistry.getChatModel()
                    .chat(UserMessage.from(userPrompt))
                    .aiMessage()
                    .text();
        }
        return llmModelRegistry.getChatModel()
                .chat(SystemMessage.from(systemPrompt), UserMessage.from(userPrompt))
                .aiMessage()
                .text();
    }

    @Override
    public void stream(String systemPrompt, String userPrompt, StreamHandler handler) {
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(SystemMessage.from(systemPrompt));
        }
        messages.add(UserMessage.from(userPrompt));
        llmModelRegistry.getStreamingChatModel().chat(messages, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                handler.onPartial(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                handler.onComplete(response.aiMessage().text());
            }

            @Override
            public void onError(Throwable error) {
                handler.onError(error);
            }
        });
    }
}
