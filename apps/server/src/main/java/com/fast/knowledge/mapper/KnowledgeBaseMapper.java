package com.fast.knowledge.mapper;

import com.fast.knowledge.model.entity.KnowledgeBase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KnowledgeBaseMapper {

    KnowledgeBase findById(@Param("id") Long id);

    List<KnowledgeBase> findByOwnerOrMember(@Param("userId") Long userId);

    int insert(KnowledgeBase kb);

    int update(KnowledgeBase kb);

    int deleteById(@Param("id") Long id);
}
