package com.fast.knowledge.service;

/**
 * 文档索引入口服务接口 — 负责调度文档解析、分块、向量化与落库。
 */
public interface DocumentIngestService {

    /** 异步调度单个文档索引 */
    void scheduleIndex(Long documentId);

    /** 定时轮询待处理任务并调度 */
    void pollPendingTasks();

    /** 同步执行完整索引流水线 */
    void indexDocument(Long documentId);
}
