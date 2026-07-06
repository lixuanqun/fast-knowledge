package com.fast.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fast.knowledge.model.entity.WikiCompileTask;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface WikiCompileTaskMapper extends BaseMapper<WikiCompileTask> {

    default WikiCompileTask findByDocumentId(Long documentId) {
        return selectOne(Wrappers.<WikiCompileTask>lambdaQuery()
                .eq(WikiCompileTask::getDocumentId, documentId)
                .orderByDesc(WikiCompileTask::getId)
                .last("LIMIT 1"));
    }

    default List<WikiCompileTask> findPending(int limit) {
        int safeLimit = Math.min(limit, 100);
        return selectList(Wrappers.<WikiCompileTask>lambdaQuery()
                .eq(WikiCompileTask::getStatus, "PENDING")
                .orderByAsc(WikiCompileTask::getId)
                .last("LIMIT " + safeLimit)); // safe: safeLimit is int, no SQL injection risk
    }
}
