package com.fast.knowledge.langchain4j.retrieval;

import com.fast.knowledge.ai.orchestration.retrieval.RetrievalOrchestrator;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class KbContentRetrieverFactory {

    private final RetrievalOrchestrator retrievalOrchestrator;
    private final Map<Long, KbHybridContentRetriever> retrievers = new ConcurrentHashMap<>();

    public KbContentRetrieverFactory(RetrievalOrchestrator retrievalOrchestrator) {
        this.retrievalOrchestrator = retrievalOrchestrator;
    }

    public ContentRetriever forKb(Long kbId) {
        return retrievers.computeIfAbsent(kbId, id -> new KbHybridContentRetriever(id, retrievalOrchestrator));
    }

    public void evict(Long kbId) {
        retrievers.remove(kbId);
    }
}
