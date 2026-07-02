package com.fast.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fast.knowledge.model.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    default List<ChatMessage> findMessagesBySessionId(Long sessionId) {
        return selectList(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByAsc(ChatMessage::getId));
    }

    default int deleteBySessionId(Long sessionId) {
        return delete(Wrappers.<ChatMessage>lambdaQuery().eq(ChatMessage::getSessionId, sessionId));
    }
}
