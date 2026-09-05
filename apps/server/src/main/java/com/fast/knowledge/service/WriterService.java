package com.fast.knowledge.service;

import com.fast.knowledge.ai.port.ChatPort;
import com.fast.knowledge.ai.orchestration.WriterGraphService;
import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.common.SseEmitterHelper;
import com.fast.knowledge.model.dto.WriterRequest;
import com.fast.knowledge.security.UserContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.Executor;

@Service
public class WriterService {

    private final RagService ragService;
    private final ChatPort chatPort;
    private final com.fast.knowledge.config.KnowledgeProperties properties;
    private final WriterGraphService writerGraphService;
    private final Executor chatExecutor;

    public WriterService(RagService ragService,
                         ChatPort chatPort,
                         com.fast.knowledge.config.KnowledgeProperties properties,
                         WriterGraphService writerGraphService,
                         @Qualifier("chatExecutor") Executor chatExecutor) {
        this.ragService = ragService;
        this.chatPort = chatPort;
        this.properties = properties;
        this.writerGraphService = writerGraphService;
        this.chatExecutor = chatExecutor;
    }

    public SseEmitter generate(WriterRequest request) {
        if (request.getTopic() == null || request.getTopic().isBlank()) {
            throw new BusinessException("请填写文档主题");
        }
        SseEmitter emitter = SseEmitterHelper.create(SseEmitterHelper.TIMEOUT_LONG);
        chatExecutor.execute(UserContext.wrap(() -> {
            try {
                String context = "";
                if (request.getKbId() != null) {
                    context = ragService.buildContext(request.getKbId(), request.getTopic());
                }
                String systemPrompt = "你是 Fast Knowledge 快速知识库的文档编写助手。请根据用户要求与参考资料撰写结构清晰的中文文档。"
                        + "使用 Markdown 格式，包含适当标题与段落。";
                String userPrompt = "主题：" + request.getTopic() + "\n"
                        + "大纲：" + (request.getOutline() != null ? request.getOutline() : "（无）") + "\n"
                        + "风格：" + (request.getStyle() != null ? request.getStyle() : "正式、专业") + "\n"
                        + "目标字数：" + (request.getWordCount() != null ? request.getWordCount() : "800") + "\n\n"
                        + "参考资料：\n" + (context.isBlank() ? "（无）" : context);

                // 多步编排（大纲→分节→引用→润色）：开关开启时走 langgraph4j 图，[DONE] 契约不变
                if (properties.getWriter().isGraphEnabled()) {
                    writerGraphService.generate(request, emitter, context);
                    SseEmitterHelper.sendNamed(emitter, "done", "[DONE]");
                    emitter.complete();
                    return;
                }

                chatPort.stream(systemPrompt, userPrompt, new ChatPort.StreamHandler() {
                    @Override
                    public void onPartial(String partialResponse) {
                        SseEmitterHelper.sendData(emitter, partialResponse);
                    }

                    @Override
                    public void onComplete(String fullText) {
                        SseEmitterHelper.sendNamed(emitter, "done", "[DONE]");
                        emitter.complete();
                    }

                    @Override
                    public void onError(Throwable error) {
                        SseEmitterHelper.sendError(emitter, error.getMessage());
                    }
                });
            } catch (Exception e) {
                SseEmitterHelper.sendError(emitter, e.getMessage());
            }
        }));
        return emitter;
    }
}
