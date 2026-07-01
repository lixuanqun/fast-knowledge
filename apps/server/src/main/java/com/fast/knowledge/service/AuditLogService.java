package com.fast.knowledge.service;

import com.fast.knowledge.mapper.AuditLogMapper;
import com.fast.knowledge.model.entity.AuditLog;
import com.fast.knowledge.security.UserContext;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;

    public AuditLogService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    public void log(String action, String targetType, Long targetId, String detail) {
        AuditLog log = new AuditLog();
        log.setUserId(UserContext.currentUserId());
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetail(detail);
        auditLogMapper.insert(log);
    }

    public List<AuditLog> recent(int limit) {
        return auditLogMapper.findRecent(limit);
    }
}
