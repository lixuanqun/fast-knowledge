package com.fast.knowledge.langchain4j.retrieval;

import com.fast.knowledge.service.WikiAwareRetrievalService;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class KbContentRetrieverFactory {

    private final WikiAwareRetrievalService wikiAwareRetrievalService;
    private final Map<Long, KbHybridContentRetriever> retrievers = new ConcurrentHashMap<>();

    public KbContentRetrieverFactory(WikiAwareRetrievalService wikiAwareRetrievalService) {
        this.wikiAwareRetrievalService = wikiAwareRetrievalService;
    }

    public ContentRetriever forKb(Long kbId) {
        return retrievers.computeIfAbsent(kbId, id -> new KbHybridContentRetriever(id, wikiAwareRetrievalService));
    }

    public void evict(Long kbId) {
        retrievers.remove(kbId);
    }
}
