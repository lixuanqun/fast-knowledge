package com.fast.knowledge.service;

import com.fast.knowledge.mapper.DocumentMapper;
import com.fast.knowledge.mapper.KnowledgeBaseMapper;
import com.fast.knowledge.model.entity.KbDocument;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DashboardService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final DocumentMapper documentMapper;
    private final IndexTaskService indexTaskService;
    private final AuditLogService auditLogService;

    public DashboardService(KnowledgeBaseMapper knowledgeBaseMapper,
                            DocumentMapper documentMapper,
                            IndexTaskService indexTaskService,
                            AuditLogService auditLogService) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.documentMapper = documentMapper;
        this.indexTaskService = indexTaskService;
        this.auditLogService = auditLogService;
    }

    public DashboardVO stats(Long userId) {
        DashboardVO vo = new DashboardVO();
        vo.setKbCount(knowledgeBaseMapper.findByOwnerOrMember(userId).size());
        int docCount = 0, indexed = 0, failed = 0;
        for (var kb : knowledgeBaseMapper.findByOwnerOrMember(userId)) {
            List<KbDocument> docs = documentMapper.findByKbId(kb.getId());
            docCount += docs.size();
            for (KbDocument d : docs) {
                if ("INDEXED".equals(d.getIndexStatus())) indexed++;
                if ("FAILED".equals(d.getIndexStatus())) failed++;
            }
        }
        vo.setDocumentCount(docCount);
        vo.setIndexedCount(indexed);
        vo.setFailedCount(failed);
        vo.setPendingTasks(indexTaskService.listPending(10).size());
        vo.setRecentAudits(auditLogService.recent(10));
        return vo;
    }

    @Data
    public static class DashboardVO {
        private int kbCount;
        private int documentCount;
        private int indexedCount;
        private int failedCount;
        private int pendingTasks;
        private Object recentAudits;
    }
}
