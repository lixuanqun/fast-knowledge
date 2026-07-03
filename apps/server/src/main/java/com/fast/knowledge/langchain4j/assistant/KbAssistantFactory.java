package com.fast.knowledge.langchain4j.assistant;

import com.fast.knowledge.langchain4j.retrieval.KbRetrievalAugmentorFactory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class KbAssistantFactory {

    private final ChatModel chatModel;
    private final KbRetrievalAugmentorFactory retrievalAugmentorFactory;
    private final Map<Long, KbAssistant> assistants = new ConcurrentHashMap<>();

    public KbAssistantFactory(ChatModel chatModel, KbRetrievalAugmentorFactory retrievalAugmentorFactory) {
        this.chatModel = chatModel;
        this.retrievalAugmentorFactory = retrievalAugmentorFactory;
    }

    public KbAssistant forKb(Long kbId) {
        return assistants.computeIfAbsent(kbId, id -> AiServices.builder(KbAssistant.class)
                .chatModel(chatModel)
                .retrievalAugmentor(retrievalAugmentorFactory.forQa(id))
                .build());
    }

    public void evict(Long kbId) {
        assistants.remove(kbId);
    }
}
