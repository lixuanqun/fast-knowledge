package com.fast.knowledge.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fast.knowledge.mapper.AuditLogMapper;
import com.fast.knowledge.model.entity.AuditLog;
import com.fast.knowledge.security.UserContext;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class AuditLogService {

    private static final DateTimeFormatter CSV_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AuditLogMapper auditLogMapper;

    public AuditLogService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    public void log(String action, String targetType, Long targetId, String detail) {
        log(UserContext.currentUserId(), action, targetType, targetId, detail);
    }

    public void log(Long userId, String action, String targetType, Long targetId, String detail) {
        AuditLog log = new AuditLog();
        log.setUserId(userId);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetail(truncate(detail, 1000));
        auditLogMapper.insert(log);
    }

    public List<AuditLog> recent(int limit) {
        return auditLogMapper.findRecent(limit);
    }

    public IPage<AuditLog> page(int pageNum, int pageSize, Long userId, String action,
                                LocalDateTime from, LocalDateTime to) {
        Page<AuditLog> page = new Page<>(pageNum, pageSize);
        return auditLogMapper.findPage(page, userId, action, from, to);
    }

    public String exportCsv(Long userId, String action, LocalDateTime from, LocalDateTime to, int maxRows) {
        IPage<AuditLog> result = page(1, maxRows, userId, action, from, to);
        StringBuilder sb = new StringBuilder();
        sb.append("id,user_id,action,target_type,target_id,detail,created_at\n");
        for (AuditLog row : result.getRecords()) {
            sb.append(row.getId()).append(',');
            sb.append(row.getUserId() != null ? row.getUserId() : "").append(',');
            sb.append(csvEscape(row.getAction())).append(',');
            sb.append(csvEscape(row.getTargetType())).append(',');
            sb.append(row.getTargetId() != null ? row.getTargetId() : "").append(',');
            sb.append(csvEscape(row.getDetail())).append(',');
            sb.append(row.getCreatedAt() != null ? CSV_TIME.format(row.getCreatedAt()) : "").append('\n');
        }
        return sb.toString();
    }

    private static String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private static String truncate(String detail, int max) {
        if (detail == null) {
            return null;
        }
        return detail.length() <= max ? detail : detail.substring(0, max);
    }
}
