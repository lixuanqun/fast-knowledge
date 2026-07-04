package com.fast.knowledge.langchain4j;

import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.llm.LlmModelRegistry;
import com.fast.knowledge.mapper.ChatMessageMapper;
import com.fast.knowledge.model.entity.ChatMessage;
import com.fast.knowledge.service.QueryRewriter;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LlmQueryRewriter implements QueryRewriter {

    private static final Logger log = LoggerFactory.getLogger(LlmQueryRewriter.class);

    private static final String REWRITE_PROMPT = """
            你是一个查询改写助手。根据对话历史，将用户的当前问题改写为一个完整、独立的检索查询。
            规则：
            1. 将代词（它、他、她、这个、那个、其等）替换为具体的指代对象
            2. 补全省略的主语、宾语
            3. 不要添加用户没有问的内容
            4. 不要改变问题的意图
            5. 如果当前问题已经是完整独立的，原样返回
            6. 只返回改写后的问题，不要加任何解释或前缀""";

    private final LlmModelRegistry llmModelRegistry;
    private final ChatMessageMapper chatMessageMapper;
    private final int historyRounds;
    private final boolean enabled;

    public LlmQueryRewriter(LlmModelRegistry llmModelRegistry,
                            ChatMessageMapper chatMessageMapper,
                            KnowledgeProperties properties) {
        this.llmModelRegistry = llmModelRegistry;
        this.chatMessageMapper = chatMessageMapper;
        this.historyRounds = Math.max(1, properties.getQueryRewrite().getHistoryRounds());
        this.enabled = properties.getQueryRewrite().isEnabled();
    }

    @Override
    public String rewrite(Long sessionId, String currentMessage) {
        if (!enabled) {
            return currentMessage;
        }
        try {
            List<ChatMessage> history = chatMessageMapper.findMessagesBySessionId(sessionId);
            if (history.isEmpty()) {
                return currentMessage;
            }

            int maxMessages = historyRounds * 2;
            List<ChatMessage> recentHistory = history.size() > maxMessages
                    ? history.subList(history.size() - maxMessages, history.size())
                    : history;

            StringBuilder historyText = new StringBuilder();
            for (ChatMessage msg : recentHistory) {
                String role = "user".equals(msg.getRole()) ? "用户" : "助手";
                historyText.append(role).append("：").append(msg.getContent()).append("\n");
            }

            String userPrompt = "对话历史：\n" + historyText + "\n当前问题：" + currentMessage + "\n\n改写后的问题：";

            String rewritten = llmModelRegistry.getChatModel()
                    .chat(SystemMessage.from(REWRITE_PROMPT), UserMessage.from(userPrompt))
                    .aiMessage()
                    .text();

            if (rewritten == null || rewritten.isBlank() || rewritten.equals(currentMessage)) {
                return currentMessage;
            }

            log.debug("Query rewrite: '{}' -> '{}'", currentMessage, rewritten);
            return rewritten.trim();
        } catch (Exception e) {
            log.warn("Query rewrite failed, falling back to original: {}", e.getMessage());
            return currentMessage;
        }
    }
}
