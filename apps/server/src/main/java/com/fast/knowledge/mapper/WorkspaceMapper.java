package com.fast.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fast.knowledge.model.entity.Workspace;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface WorkspaceMapper extends BaseMapper<Workspace> {

    default List<Workspace> findByOwnerOrMember(Long userId) {
        return selectList(Wrappers.<Workspace>lambdaQuery()
                .eq(Workspace::getOwnerId, userId)
                .orderByDesc(Workspace::getUpdatedAt));
    }

    @Select("SELECT * FROM kb_workspace ORDER BY id LIMIT 1")
    Workspace findDefault();
}
