package com.fast.knowledge.langchain4j.retrieval;

import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import org.springframework.stereotype.Component;

/**
 * 统一构建 LangChain4j RetrievalAugmentor：单轮问答与多轮对话均直通检索，
 * 查询改写由各调用方自行控制（QA 不改写，对话由 LlmQueryRewriter 统一改写）。
 */
@Component
public class KbRetrievalAugmentorFactory {

    private final KbContentRetrieverFactory contentRetrieverFactory;

    public KbRetrievalAugmentorFactory(KbContentRetrieverFactory contentRetrieverFactory) {
        this.contentRetrieverFactory = contentRetrieverFactory;
    }

    /** 单轮 RAG 问答：查询不做压缩，直接检索。 */
    public RetrievalAugmentor forQa(Long kbId) {
        return DefaultRetrievalAugmentor.builder()
                .contentRetriever(contentRetrieverFactory.forKb(kbId))
                .build();
    }

    /**
     * 多轮对话 RAG：查询改写由 ChatServiceImpl 的 LlmQueryRewriter 统一完成，
     * 此处不再叠加 CompressingQueryTransformer，避免双重 LLM 压缩导致关键词丢失、召回偏移。
     */
    public RetrievalAugmentor forChat(Long kbId) {
        return DefaultRetrievalAugmentor.builder()
                .contentRetriever(contentRetrieverFactory.forKb(kbId))
                .build();
    }
}
