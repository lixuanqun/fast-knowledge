package com.fast.knowledge.service;

import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.common.SseEmitterHelper;
import com.fast.knowledge.model.dto.WriterRequest;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.Executor;

@Service
public class WriterService {

    private final RagService ragService;
    private final StreamingChatModel streamingChatModel;
    private final Executor chatExecutor;

    public WriterService(RagService ragService,
                         StreamingChatModel streamingChatModel,
                         @Qualifier("chatExecutor") Executor chatExecutor) {
        this.ragService = ragService;
        this.streamingChatModel = streamingChatModel;
        this.chatExecutor = chatExecutor;
    }

    public SseEmitter generate(WriterRequest request) {
        if (request.getTopic() == null || request.getTopic().isBlank()) {
            throw new BusinessException("请填写文档主题");
        }
        SseEmitter emitter = SseEmitterHelper.create(SseEmitterHelper.TIMEOUT_LONG);
        chatExecutor.execute(() -> {
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

                List<dev.langchain4j.data.message.ChatMessage> messages = List.of(
                        SystemMessage.from(systemPrompt),
                        UserMessage.from(userPrompt)
                );

                streamingChatModel.chat(messages, new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String partialResponse) {
                        SseEmitterHelper.sendData(emitter, partialResponse);
                    }

                    @Override
                    public void onCompleteResponse(ChatResponse response) {
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
        });
        return emitter;
    }
}
