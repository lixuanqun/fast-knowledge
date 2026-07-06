package com.fast.knowledge.service.impl;

import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.model.dto.QaRequest;
import com.fast.knowledge.model.dto.SearchRequest;
import com.fast.knowledge.model.vo.QaResponseVO;
import com.fast.knowledge.model.vo.SearchHitVO;
import com.fast.knowledge.service.AuditLogService;
import com.fast.knowledge.service.MetricsService;
import com.fast.knowledge.service.SearchService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagServiceImplTest {

    @Mock private SearchService searchService;
    @Mock private ChatModel chatModel;
    @Mock private AuditLogService auditLogService;
    @Mock private MetricsService metricsService;

    private RagServiceImpl ragService;

    @BeforeEach
    void setUp() throws Exception {
        ragService = new RagServiceImpl(searchService, chatModel, auditLogService, metricsService);
        // timeRag should execute the wrapped Callable (for tests); lenient because validation tests don't reach it
        lenient().when(metricsService.timeRag(any())).thenAnswer(inv -> {
            Callable<?> action = inv.getArgument(0);
            return action.call();
        });
    }

    @Test
    void shouldRejectMissingKbId() {
        QaRequest request = new QaRequest();
        request.setQuestion("test question");
        assertThatThrownBy(() -> ragService.ask(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("参数不完整");
    }

    @Test
    void shouldRejectBlankQuestion() {
        QaRequest request = new QaRequest();
        request.setKbId(1L);
        request.setQuestion("   ");
        assertThatThrownBy(() -> ragService.ask(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("参数不完整");
    }

    @Test
    void shouldAnswerBasedOnContext() throws Exception {
        QaRequest request = new QaRequest();
        request.setKbId(1L);
        request.setQuestion("什么是Java");

        List<SearchHitVO> hits = List.of(hit("Java简介", "Java是一种编程语言"));
        when(searchService.search(any(SearchRequest.class))).thenReturn(hits);
        when(chatModel.chat(any(dev.langchain4j.data.message.ChatMessage[].class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(dev.langchain4j.data.message.AiMessage.from("Java是一种面向对象的编程语言"))
                        .build());

        QaResponseVO result = ragService.ask(request);

        assertThat(result.getAnswer()).contains("Java");
        assertThat(result.getSources()).hasSize(1);
        verify(auditLogService).log(any(), any(), any(), any());
    }

    @Test
    void shouldReportNoContentWhenContextEmpty() throws Exception {
        QaRequest request = new QaRequest();
        request.setKbId(1L);
        request.setQuestion("unknown topic");

        when(searchService.search(any(SearchRequest.class))).thenReturn(Collections.emptyList());
        when(chatModel.chat(any(dev.langchain4j.data.message.ChatMessage[].class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(dev.langchain4j.data.message.AiMessage.from("知识库中未找到相关内容"))
                        .build());

        QaResponseVO result = ragService.ask(request);
        assertThat(result.getAnswer()).contains("知识库中未找到相关内容");
    }

    private SearchHitVO hit(String title, String content) {
        SearchHitVO vo = new SearchHitVO();
        vo.setDocumentTitle(title);
        vo.setContent(content);
        vo.setScore(0.9);
        vo.setDocumentId(1L);
        vo.setChunkId(1L);
        return vo;
    }
}
