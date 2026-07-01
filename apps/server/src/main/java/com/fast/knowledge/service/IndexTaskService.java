package com.fast.knowledge.service;

import com.fast.knowledge.mapper.IndexTaskMapper;
import com.fast.knowledge.model.entity.IndexTask;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class IndexTaskService {

    private final IndexTaskMapper indexTaskMapper;
    private final DocumentIngestService documentIngestService;

    public IndexTaskService(IndexTaskMapper indexTaskMapper, DocumentIngestService documentIngestService) {
        this.indexTaskMapper = indexTaskMapper;
        this.documentIngestService = documentIngestService;
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
        return indexTaskMapper.findFailedByKbId(kbId, limit);
    }

    public void retry(Long documentId) {
        documentIngestService.scheduleIndex(documentId);
    }
}
