package com.fast.knowledge.ai.orchestration;

import com.fast.knowledge.ai.port.ChatPort;
import com.fast.knowledge.model.dto.WriterRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WriterGraphServiceTest {

    @Mock private ChatPort chatPort;

    private WriterGraphService service;

    @BeforeEach
    void setUp() {
        service = new WriterGraphService(chatPort, new ObjectMapper());
    }

    private WriterRequest request() {
        WriterRequest request = new WriterRequest();
        request.setTopic("Fast Knowledge 部署指南");
        request.setStyle("正式、专业");
        request.setWordCount(1200);
        return request;
    }

    @Test
    void parsesNumberedOutlineLines() {
        List<String> sections = WriterGraphService.parseOutline(
                "以下是建议大纲：\n1. 背景介绍\n2、安装步骤\n3) 验证与验收\n正文不属于大纲行");
        assertThat(sections).containsExactly("背景介绍", "安装步骤", "验证与验收");
        // 无编号行时回退单节
        assertThat(WriterGraphService.parseOutline("只有一段文字")).containsExactly("概述");
    }

    @Test
    void runsOutlineSectionCitePolishFlowWithStreaming() throws Exception {
        when(chatPort.complete(anyString(), anyString()))
                .thenReturn("1. 背景介绍\n2. 安装步骤");
        doAnswer(invocation -> {
            ChatPort.StreamHandler handler = invocation.getArgument(2);
            handler.onPartial("片段内容。");
            handler.onComplete("## 完整小节内容\n\n- 要点一\n- 要点二");
            return null;
        }).when(chatPort).stream(anyString(), anyString(), any());

        SseEmitter emitter = new SseEmitter(0L);
        service.generate(request(), emitter, "参考资料A\n参考资料B");

        // 大纲 1 次 + 分节 2 次 + 润色 1 次 = 3 次 stream
        verify(chatPort, times(3)).stream(anyString(), anyString(), any());
        verify(chatPort).complete(contains("大纲"), anyString());

        // 润色节点：system prompt 为润色指令，user prompt 为「标题 + 各小节 + 参考资料」全文
        org.mockito.ArgumentCaptor<String> systems = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<String> prompts = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(chatPort, times(3)).stream(systems.capture(), prompts.capture(), any());
        assertThat(systems.getAllValues().get(2)).contains("润色");
        assertThat(prompts.getAllValues().get(2)).contains("完整小节内容", "参考资料A");
    }

    @Test
    void emptyOutlineFallsBackToSingleSection() throws Exception {
        when(chatPort.complete(anyString(), anyString())).thenReturn("（无大纲）");
        doAnswer(invocation -> {
            ChatPort.StreamHandler handler = invocation.getArgument(2);
            handler.onComplete("## 内容");
            return null;
        }).when(chatPort).stream(anyString(), anyString(), any());

        service.generate(request(), new SseEmitter(0L), "");

        // 大纲解析为「概述」单节：分节 1 次 + 润色 1 次
        verify(chatPort, times(2)).stream(anyString(), anyString(), any());
    }
}
