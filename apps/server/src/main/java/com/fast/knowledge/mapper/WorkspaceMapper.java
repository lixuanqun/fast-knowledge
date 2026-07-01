package com.fast.knowledge.mapper;

import com.fast.knowledge.model.entity.Workspace;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WorkspaceMapper {
    Workspace findById(@Param("id") Long id);

    List<Workspace> findByOwnerOrMember(@Param("userId") Long userId);

    Workspace findDefault();

    int insert(Workspace workspace);

    int update(Workspace workspace);
}
