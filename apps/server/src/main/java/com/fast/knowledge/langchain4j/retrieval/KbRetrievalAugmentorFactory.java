package com.fast.knowledge.langchain4j.retrieval;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.query.transformer.CompressingQueryTransformer;
import org.springframework.stereotype.Component;

/**
 * 统一构建 LangChain4j RetrievalAugmentor：单轮问答用直通检索，多轮对话用查询压缩。
 */
@Component
public class KbRetrievalAugmentorFactory {

    private final ChatModel chatModel;
    private final KbContentRetrieverFactory contentRetrieverFactory;

    public KbRetrievalAugmentorFactory(ChatModel chatModel,
                                       KbContentRetrieverFactory contentRetrieverFactory) {
        this.chatModel = chatModel;
        this.contentRetrieverFactory = contentRetrieverFactory;
    }

    /** 单轮 RAG 问答：查询不做压缩，直接检索。 */
    public RetrievalAugmentor forQa(Long kbId) {
        return DefaultRetrievalAugmentor.builder()
                .contentRetriever(contentRetrieverFactory.forKb(kbId))
                .build();
    }

    /** 多轮对话 RAG：结合历史将追问压缩为独立检索查询。 */
    public RetrievalAugmentor forChat(Long kbId) {
        return DefaultRetrievalAugmentor.builder()
                .queryTransformer(CompressingQueryTransformer.builder()
                        .chatModel(chatModel)
                        .build())
                .contentRetriever(contentRetrieverFactory.forKb(kbId))
                .build();
    }
}
