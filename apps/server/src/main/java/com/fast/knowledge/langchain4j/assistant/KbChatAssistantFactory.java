package com.fast.knowledge.langchain4j.assistant;

import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.langchain4j.memory.DbChatMemoryStore;
import com.fast.knowledge.langchain4j.retrieval.KbContentRetrieverFactory;
import com.fast.knowledge.langchain4j.retrieval.KbRetrievalAugmentorFactory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class KbChatAssistantFactory {

    private final int memoryWindow;

    private final StreamingChatModel streamingChatModel;
    private final DbChatMemoryStore chatMemoryStore;
    private final KbRetrievalAugmentorFactory retrievalAugmentorFactory;
    private final KbContentRetrieverFactory contentRetrieverFactory;
    private final GeneralChatAssistant generalAssistant;
    private final Map<Long, KbChatAssistant> kbAssistants = new ConcurrentHashMap<>();

    public KbChatAssistantFactory(StreamingChatModel streamingChatModel,
                                  DbChatMemoryStore chatMemoryStore,
                                  KbRetrievalAugmentorFactory retrievalAugmentorFactory,
                                  KbContentRetrieverFactory contentRetrieverFactory,
                                  KnowledgeProperties properties) {
        this.streamingChatModel = streamingChatModel;
        this.chatMemoryStore = chatMemoryStore;
        this.retrievalAugmentorFactory = retrievalAugmentorFactory;
        this.contentRetrieverFactory = contentRetrieverFactory;
        this.memoryWindow = Math.max(1, properties.getChat().getMemoryWindow());
        this.generalAssistant = AiServices.builder(GeneralChatAssistant.class)
                .streamingChatModel(streamingChatModel)
                .chatMemoryProvider(this::chatMemory)
                .storeRetrievedContentInChatMemory(false)
                .build();
    }

    public TokenStream stream(Long kbId, Long sessionId, String message) {
        if (kbId == null) {
            return generalAssistant.chat(sessionId, message);
        }
        return forKb(kbId).chat(sessionId, message);
    }

    public KbChatAssistant forKb(Long kbId) {
        return kbAssistants.computeIfAbsent(kbId, id -> AiServices.builder(KbChatAssistant.class)
                .streamingChatModel(streamingChatModel)
                .chatMemoryProvider(this::chatMemory)
                .retrievalAugmentor(retrievalAugmentorFactory.forChat(id))
                .storeRetrievedContentInChatMemory(false)
                .build());
    }

    public void evict(Long kbId) {
        if (kbId != null) {
            kbAssistants.remove(kbId);
        }
        contentRetrieverFactory.evict(kbId);
    }

    private MessageWindowChatMemory chatMemory(Object memoryId) {
        return MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(memoryWindow)
                .chatMemoryStore(chatMemoryStore)
                .build();
    }
}
