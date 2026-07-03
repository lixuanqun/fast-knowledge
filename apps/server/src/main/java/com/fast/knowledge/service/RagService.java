package com.fast.knowledge.service;

import com.fast.knowledge.audit.AuditActions;
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

    private static final String QA_SYSTEM_PROMPT = """
            你是 Fast Knowledge 快速知识库助手。请仅根据提供的参考资料回答问题。
            若参考资料不足以回答，请明确说明「知识库中未找到相关内容」，不要编造。
            回答请使用简体中文，条理清晰。""";

    private final SearchService searchService;
    private final ChatModel chatModel;
    private final AuditLogService auditLogService;

    public RagService(SearchService searchService, ChatModel chatModel, AuditLogService auditLogService) {
        this.searchService = searchService;
        this.chatModel = chatModel;
        this.auditLogService = auditLogService;
    }

    public QaResponseVO ask(QaRequest request) throws Exception {
        if (request.getKbId() == null || request.getQuestion() == null || request.getQuestion().isBlank()) {
            throw new BusinessException("参数不完整");
        }

        RagContextVO rag = retrieve(request.getKbId(), request.getQuestion());
        String userPrompt = rag.getContext().isBlank()
                ? "问题：" + request.getQuestion()
                : "参考资料：\n" + rag.getContext() + "\n\n问题：" + request.getQuestion();
        String answer = chatModel.chat(SystemMessage.from(QA_SYSTEM_PROMPT), UserMessage.from(userPrompt))
                .aiMessage()
                .text();

        QaResponseVO vo = new QaResponseVO();
        vo.setAnswer(answer);
        vo.setSources(rag.getSources());
        auditLogService.log(AuditActions.QA, "KB", request.getKbId(),
                "question=" + truncate(request.getQuestion(), 200));
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

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
