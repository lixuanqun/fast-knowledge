package com.fast.knowledge.langchain4j.adapter;

import com.fast.knowledge.ai.port.RerankPort;
import com.fast.knowledge.langchain4j.rerank.SearchRerankService;
import com.fast.knowledge.model.vo.SearchHitVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LangChain4jRerankAdapter implements RerankPort {

    private final SearchRerankService searchRerankService;

    public LangChain4jRerankAdapter(SearchRerankService searchRerankService) {
        this.searchRerankService = searchRerankService;
    }

    @Override
    public boolean isActive() {
        return searchRerankService.isActive();
    }

    @Override
    public int candidateCount(int topK) {
        return searchRerankService.candidateCount(topK);
    }

    @Override
    public List<SearchHitVO> rerank(String query, List<SearchHitVO> hits, int topK) {
        return searchRerankService.rerank(query, hits, topK);
    }
}
