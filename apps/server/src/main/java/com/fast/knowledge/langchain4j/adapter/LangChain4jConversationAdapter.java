package com.fast.knowledge.langchain4j.adapter;

import com.fast.knowledge.ai.port.ConversationPort;
import com.fast.knowledge.langchain4j.RetrievedContentMapper;
import com.fast.knowledge.langchain4j.assistant.KbChatAssistantFactory;
import com.fast.knowledge.langchain4j.memory.DbChatMemoryStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class LangChain4jConversationAdapter implements ConversationPort {

    private final KbChatAssistantFactory kbChatAssistantFactory;
    private final DbChatMemoryStore chatMemoryStore;

    public LangChain4jConversationAdapter(KbChatAssistantFactory kbChatAssistantFactory,
                                          DbChatMemoryStore chatMemoryStore) {
        this.kbChatAssistantFactory = kbChatAssistantFactory;
        this.chatMemoryStore = chatMemoryStore;
    }

    @Override
    public void streamConversation(Long kbId, Long sessionId, String message, ConversationHandler handler) {
        List<com.fast.knowledge.model.vo.SearchHitVO> sources = new ArrayList<>();
        kbChatAssistantFactory.stream(kbId, sessionId, message)
                .onRetrieved(contents -> {
                    sources.addAll(RetrievedContentMapper.toSearchHits(contents));
                    handler.onRetrieved(sources);
                })
                .onPartialResponse(handler::onPartial)
                .onCompleteResponse(response -> handler.onComplete())
                .onError(handler::onError)
                .start();
    }

    @Override
    public void evictAssistant(Long kbId) {
        kbChatAssistantFactory.evict(kbId);
    }

    @Override
    public void deleteMemory(Long sessionId) {
        chatMemoryStore.deleteMessages(sessionId);
    }

    @Override
    public void attachSources(Long sessionId, String sourcesJson) {
        chatMemoryStore.attachSources(sessionId, sourcesJson);
    }
}
