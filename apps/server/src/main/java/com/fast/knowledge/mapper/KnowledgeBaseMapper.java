package com.fast.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fast.knowledge.model.entity.KnowledgeBase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBase> {

    @Select("""
            SELECT DISTINCT kb.* FROM kb_knowledge_base kb
            LEFT JOIN kb_kb_member m ON kb.id = m.kb_id
            WHERE kb.owner_id = #{userId} OR m.user_id = #{userId} OR kb.visibility = 'PUBLIC'
            ORDER BY kb.updated_at DESC
            """)
    List<KnowledgeBase> findByOwnerOrMember(@Param("userId") Long userId);
}
