package com.fast.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fast.knowledge.model.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    default List<ChatSession> findSessionsByUserId(Long userId) {
        return selectList(Wrappers.<ChatSession>lambdaQuery()
                .eq(ChatSession::getUserId, userId)
                .orderByDesc(ChatSession::getUpdatedAt));
    }

    @Update("UPDATE kb_chat_session SET updated_at = #{updatedAt} WHERE id = #{sessionId}")
    int touchSession(@Param("sessionId") Long sessionId, @Param("updatedAt") LocalDateTime updatedAt);
}
