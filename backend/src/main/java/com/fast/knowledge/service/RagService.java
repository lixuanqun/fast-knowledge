package com.fast.knowledge.service;

import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.model.dto.QaRequest;
import com.fast.knowledge.model.dto.SearchRequest;
import com.fast.knowledge.model.vo.QaResponseVO;
import com.fast.knowledge.model.vo.RagContextVO;
import com.fast.knowledge.model.vo.SearchHitVO;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RagService {

    private final SearchService searchService;
    private final ChatModel chatModel;

    public RagService(SearchService searchService, ChatModel chatModel) {
        this.searchService = searchService;
        this.chatModel = chatModel;
    }

    public QaResponseVO ask(QaRequest request) throws Exception {
        if (request.getKbId() == null || request.getQuestion() == null || request.getQuestion().isBlank()) {
            throw new BusinessException("参数不完整");
        }

        RagContextVO rag = retrieve(request.getKbId(), request.getQuestion());

        String systemPrompt = "你是 Fast Knowledge 快速知识库助手。请仅根据提供的参考资料回答问题。"
                + "若参考资料不足以回答，请明确说明「知识库中未找到相关内容」，不要编造。"
                + "回答请使用简体中文，条理清晰。";
        String userPrompt = "参考资料：\n" + (rag.getContext().isBlank() ? "（无）" : rag.getContext())
                + "\n\n用户问题：" + request.getQuestion();

        String answer = chatModel.chat(SystemMessage.from(systemPrompt), UserMessage.from(userPrompt))
                .aiMessage().text();

        QaResponseVO vo = new QaResponseVO();
        vo.setAnswer(answer);
        vo.setSources(rag.getSources());
        return vo;
    }

    public RagContextVO retrieve(Long kbId, String query) throws Exception {
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.setKbId(kbId);
        searchRequest.setQuery(query);
        List<SearchHitVO> hits = searchService.search(searchRequest);
        String context = formatHits(hits);
        return new RagContextVO(context, hits);
    }

    public String buildContext(Long kbId, String query) throws Exception {
        return retrieve(kbId, query).getContext();
    }

    private String formatHits(List<SearchHitVO> hits) {
        return hits.stream()
                .map(h -> "【" + h.getDocumentTitle() + "】\n" + h.getContent())
                .collect(Collectors.joining("\n\n"));
    }
}
