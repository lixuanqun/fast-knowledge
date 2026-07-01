package com.fast.knowledge.mapper;

import com.fast.knowledge.model.entity.ChatSession;
import com.fast.knowledge.model.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatMapper {

    int insertSession(ChatSession session);

    int updateSession(ChatSession session);

    ChatSession findSessionById(@Param("id") Long id);

    List<ChatSession> findSessionsByUserId(@Param("userId") Long userId);

    int insertMessage(ChatMessage message);

    List<ChatMessage> findMessagesBySessionId(@Param("sessionId") Long sessionId);

    int touchSession(@Param("sessionId") Long sessionId);

    int deleteMessagesBySessionId(@Param("sessionId") Long sessionId);

    int deleteSession(@Param("sessionId") Long sessionId);
}
