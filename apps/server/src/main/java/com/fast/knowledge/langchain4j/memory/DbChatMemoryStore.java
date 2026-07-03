package com.fast.knowledge.langchain4j.memory;

import com.fast.knowledge.mapper.ChatMessageMapper;
import com.fast.knowledge.mapper.ChatSessionMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * LangChain4j ChatMemory 与 chat_message 表双向同步。
 */
@Component
public class DbChatMemoryStore implements ChatMemoryStore {

    private static final int MEMORY_WINDOW = 10;

    private final ChatMessageMapper chatMessageMapper;
    private final ChatSessionMapper chatSessionMapper;

    public DbChatMemoryStore(ChatMessageMapper chatMessageMapper, ChatSessionMapper chatSessionMapper) {
        this.chatMessageMapper = chatMessageMapper;
        this.chatSessionMapper = chatSessionMapper;
    }

    @Override
    public List<dev.langchain4j.data.message.ChatMessage> getMessages(Object memoryId) {
        Long sessionId = toSessionId(memoryId);
        List<com.fast.knowledge.model.entity.ChatMessage> rows =
                chatMessageMapper.findMessagesBySessionId(sessionId);
        int start = Math.max(0, rows.size() - MEMORY_WINDOW);
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
        for (int i = start; i < rows.size(); i++) {
            dev.langchain4j.data.message.ChatMessage lc = toLcMessage(rows.get(i));
            if (lc != null) {
                messages.add(lc);
            }
        }
        return messages;
    }

    @Override
    public void updateMessages(Object memoryId, List<dev.langchain4j.data.message.ChatMessage> messages) {
        Long sessionId = toSessionId(memoryId);
        List<com.fast.knowledge.model.entity.ChatMessage> existing =
                chatMessageMapper.findMessagesBySessionId(sessionId);
        if (messages.size() <= existing.size()) {
            return;
        }
        for (int i = existing.size(); i < messages.size(); i++) {
            com.fast.knowledge.model.entity.ChatMessage row = toEntity(messages.get(i), sessionId);
            if (row != null) {
                chatMessageMapper.insert(row);
            }
        }
        chatSessionMapper.touchSession(sessionId, LocalDateTime.now());
    }

    @Override
    public void deleteMessages(Object memoryId) {
        chatMessageMapper.deleteBySessionId(toSessionId(memoryId));
    }

    public void attachSources(Long sessionId, String sourcesJson) {
        List<com.fast.knowledge.model.entity.ChatMessage> rows =
                chatMessageMapper.findMessagesBySessionId(sessionId);
        if (rows.isEmpty()) {
            return;
        }
        com.fast.knowledge.model.entity.ChatMessage last = rows.get(rows.size() - 1);
        if ("assistant".equals(last.getRole())) {
            last.setSources(sourcesJson);
            chatMessageMapper.updateById(last);
        }
    }

    private dev.langchain4j.data.message.ChatMessage toLcMessage(com.fast.knowledge.model.entity.ChatMessage row) {
        if ("user".equals(row.getRole())) {
            return UserMessage.from(row.getContent());
        }
        if ("assistant".equals(row.getRole())) {
            return AiMessage.from(row.getContent());
        }
        return null;
    }

    private com.fast.knowledge.model.entity.ChatMessage toEntity(dev.langchain4j.data.message.ChatMessage message,
                                                                 Long sessionId) {
        com.fast.knowledge.model.entity.ChatMessage row = new com.fast.knowledge.model.entity.ChatMessage();
        row.setSessionId(sessionId);
        if (message instanceof UserMessage userMessage) {
            row.setRole("user");
            row.setContent(userMessage.singleText());
            return row;
        }
        if (message instanceof AiMessage aiMessage) {
            row.setRole("assistant");
            row.setContent(aiMessage.text());
            return row;
        }
        return null;
    }

    private Long toSessionId(Object memoryId) {
        if (memoryId instanceof Long id) {
            return id;
        }
        if (memoryId instanceof String s) {
            return Long.parseLong(s);
        }
        throw new IllegalArgumentException("memoryId 须为 sessionId（Long 或 String）");
    }
}
