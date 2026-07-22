package com.fast.knowledge.service.impl;

import com.fast.knowledge.audit.AuditActions;
import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.common.StringUtils;
import com.fast.knowledge.model.dto.QaRequest;
import com.fast.knowledge.model.vo.QaResponseVO;
import com.fast.knowledge.model.vo.RagContextVO;
import com.fast.knowledge.model.vo.SearchHitVO;
import com.fast.knowledge.service.AuditLogService;
import com.fast.knowledge.service.MetricsService;
import com.fast.knowledge.service.QaHistoryService;
import com.fast.knowledge.service.RagService;
import com.fast.knowledge.service.WikiAwareRetrievalService;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RagServiceImpl implements RagService {

    private static final String QA_SYSTEM_PROMPT = """
            你是 Fast Knowledge 快速知识库助手。请仅根据提供的参考资料回答问题。
            若参考资料不足以回答，请明确说明「知识库中未找到相关内容」，不要编造。
            回答请使用简体中文，条理清晰。""";

    private final WikiAwareRetrievalService wikiAwareRetrievalService;
    private final ChatModel chatModel;
    private final AuditLogService auditLogService;
    private final MetricsService metricsService;
    private final QaHistoryService qaHistoryService;

    public RagServiceImpl(WikiAwareRetrievalService wikiAwareRetrievalService, ChatModel chatModel,
                          AuditLogService auditLogService, MetricsService metricsService,
                          QaHistoryService qaHistoryService) {
        this.wikiAwareRetrievalService = wikiAwareRetrievalService;
        this.chatModel = chatModel;
        this.auditLogService = auditLogService;
        this.metricsService = metricsService;
        this.qaHistoryService = qaHistoryService;
    }

    @Override
    public QaResponseVO ask(QaRequest request) throws Exception {
        if (request.getKbId() == null || request.getQuestion() == null || request.getQuestion().isBlank()) {
            throw new BusinessException("参数不完整");
        }

        QaResponseVO result = metricsService.timeRag(() -> {
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
            return vo;
        });

        metricsService.countRag();
        metricsService.countLlmCall("rag");
        auditLogService.log(AuditActions.QA, "KB", request.getKbId(),
                "question=" + StringUtils.truncate(request.getQuestion(), 200)
                        + ", sources=" + (result.getSources() != null ? result.getSources().size() : 0));
        qaHistoryService.record(request.getKbId(), request.getQuestion(), result.getAnswer(), result.getSources());
        return result;
    }

    @Override
    public RagContextVO retrieve(Long kbId, String query) throws Exception {
        List<SearchHitVO> hits = wikiAwareRetrievalService.retrieve(kbId, query);
        String context = formatHits(hits);
        return new RagContextVO(context, hits);
    }

    @Override
    public String buildContext(Long kbId, String query) throws Exception {
        return retrieve(kbId, query).getContext();
    }

    private static String formatHits(List<SearchHitVO> hits) {
        return hits.stream()
                .map(h -> "【" + h.getDocumentTitle() + "】\n" + h.getContent())
                .collect(Collectors.joining("\n\n"));
    }
}
