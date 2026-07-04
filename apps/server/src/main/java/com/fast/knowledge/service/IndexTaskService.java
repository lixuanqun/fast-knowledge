package com.fast.knowledge.service;

import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.mapper.DocumentMapper;
import com.fast.knowledge.mapper.IndexTaskMapper;
import com.fast.knowledge.model.entity.IndexTask;
import com.fast.knowledge.model.entity.KbDocument;
import com.fast.knowledge.model.entity.KnowledgeBase;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class IndexTaskService {

    private final IndexTaskMapper indexTaskMapper;
    private final DocumentIngestService documentIngestService;
    private final DocumentMapper documentMapper;
    private final KnowledgeBaseService knowledgeBaseService;
    private final IndexEventPublisher indexEventPublisher;

    public IndexTaskService(IndexTaskMapper indexTaskMapper,
                            DocumentIngestService documentIngestService,
                            DocumentMapper documentMapper,
                            KnowledgeBaseService knowledgeBaseService,
                            IndexEventPublisher indexEventPublisher) {
        this.indexTaskMapper = indexTaskMapper;
        this.documentIngestService = documentIngestService;
        this.documentMapper = documentMapper;
        this.knowledgeBaseService = knowledgeBaseService;
        this.indexEventPublisher = indexEventPublisher;
    }

    public List<IndexTask> listPending(int limit) {
        return indexTaskMapper.findPending(limit);
    }

    public List<IndexTask> listRecent(int limit) {
        return indexTaskMapper.findRecent(limit);
    }

    public List<IndexTask> listFailed(int limit) {
        return indexTaskMapper.findFailed(limit);
    }

    public List<IndexTask> listFailedByKb(Long kbId, int limit) {
        knowledgeBaseService.checkReadPermission(knowledgeBaseService.getById(kbId));
        return indexTaskMapper.findFailedByKbId(kbId, limit);
    }

    /** 列出重试耗尽的任务（DEAD 状态） */
    public List<IndexTask> listDead(int limit) {
        return indexTaskMapper.findDead(limit);
    }

    public void retry(Long documentId) {
        KbDocument doc = documentMapper.selectById(documentId);
        if (doc == null) {
            throw new BusinessException("文档不存在");
        }
        KnowledgeBase kb = knowledgeBaseService.getById(doc.getKbId());
        knowledgeBaseService.checkWritePermission(kb);
        // Reset retry count for manual retry
        IndexTask task = indexTaskMapper.findByDocumentId(documentId);
        if (task != null && ("FAILED".equals(task.getStatus()) || "DEAD".equals(task.getStatus()))) {
            task.setStatus("PENDING");
            task.setRetryCount(0);
            task.setErrorMsg(null);
            indexTaskMapper.updateById(task);
        }
        // Dispatch via Pub/Sub if enabled, otherwise directly
        if (indexEventPublisher.isEnabled()) {
            indexEventPublisher.publish(documentId);
        } else {
            documentIngestService.scheduleIndex(documentId);
        }
    }
}
