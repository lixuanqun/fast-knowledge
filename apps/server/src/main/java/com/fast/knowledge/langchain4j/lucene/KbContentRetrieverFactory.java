package com.fast.knowledge.langchain4j.lucene;

import com.fast.knowledge.service.SearchService;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class KbContentRetrieverFactory {

    private final SearchService searchService;
    private final Map<Long, KbHybridContentRetriever> retrievers = new ConcurrentHashMap<>();

    public KbContentRetrieverFactory(SearchService searchService) {
        this.searchService = searchService;
    }

    public ContentRetriever forKb(Long kbId) {
        return retrievers.computeIfAbsent(kbId, id -> new KbHybridContentRetriever(id, searchService));
    }

    public void evict(Long kbId) {
        retrievers.remove(kbId);
    }
}
