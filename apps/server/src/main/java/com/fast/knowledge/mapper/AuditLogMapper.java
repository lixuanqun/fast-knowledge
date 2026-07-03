package com.fast.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fast.knowledge.model.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {

    @Select("SELECT * FROM kb_audit_log ORDER BY id DESC LIMIT #{limit}")
    List<AuditLog> findRecent(@Param("limit") int limit);

    default IPage<AuditLog> findPage(Page<AuditLog> page, Long userId, String action,
                                       LocalDateTime from, LocalDateTime to) {
        var wrapper = Wrappers.<AuditLog>lambdaQuery()
                .eq(userId != null, AuditLog::getUserId, userId)
                .eq(action != null && !action.isBlank(), AuditLog::getAction, action)
                .ge(from != null, AuditLog::getCreatedAt, from)
                .le(to != null, AuditLog::getCreatedAt, to)
                .orderByDesc(AuditLog::getId);
        return selectPage(page, wrapper);
    }
}
