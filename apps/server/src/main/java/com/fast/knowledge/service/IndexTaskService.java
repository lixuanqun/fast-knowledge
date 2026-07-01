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

    public IndexTaskService(IndexTaskMapper indexTaskMapper,
                            DocumentIngestService documentIngestService,
                            DocumentMapper documentMapper,
                            KnowledgeBaseService knowledgeBaseService) {
        this.indexTaskMapper = indexTaskMapper;
        this.documentIngestService = documentIngestService;
        this.documentMapper = documentMapper;
        this.knowledgeBaseService = knowledgeBaseService;
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

    public void retry(Long documentId) {
        KbDocument doc = documentMapper.findById(documentId);
        if (doc == null) {
            throw new BusinessException("文档不存在");
        }
        KnowledgeBase kb = knowledgeBaseService.getById(doc.getKbId());
        knowledgeBaseService.checkWritePermission(kb);
        documentIngestService.scheduleIndex(documentId);
    }
}
