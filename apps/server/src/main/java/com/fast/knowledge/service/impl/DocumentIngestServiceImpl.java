package com.fast.knowledge.service.impl;

import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.mapper.DocumentMapper;
import com.fast.knowledge.mapper.IndexTaskMapper;
import com.fast.knowledge.model.entity.IndexTask;
import com.fast.knowledge.model.entity.KbDocument;
import com.fast.knowledge.service.DocumentIngestService;
import com.fast.knowledge.service.IndexEventPublisher;
import com.fast.knowledge.service.IndexTaskProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文档索引入口服务 — 负责调度入口与任务轮询。
 *
 * <p>实际异步执行与事务管理委托给 {@link IndexTaskProcessor}，
 * 确保 {@code @Async} 和 {@code @Transactional} 通过 Spring AOP 代理生效。
 * 所有调度入口（{@link #scheduleIndex}、{@link #pollPendingTasks}）
 * 均通过注入的 processor 调用，消除 self-invocation 绕过。
 */
@Slf4j
@Service
public class DocumentIngestServiceImpl implements DocumentIngestService {

    // Exponential backoff base delays in minutes for retry 1, 2, 3
    private static final int[] RETRY_BACKOFF_MINUTES = {1, 2, 4};

    private final DocumentMapper documentMapper;
    private final IndexTaskMapper indexTaskMapper;
    private final IndexTaskProcessor processor;
    private final IndexEventPublisher indexEventPublisher;
    private final int maxRetry;

    public DocumentIngestServiceImpl(DocumentMapper documentMapper,
                                     IndexTaskMapper indexTaskMapper,
                                     IndexTaskProcessor processor,
                                     IndexEventPublisher indexEventPublisher,
                                     KnowledgeProperties properties) {
        this.documentMapper = documentMapper;
        this.indexTaskMapper = indexTaskMapper;
        this.processor = processor;
        this.indexEventPublisher = indexEventPublisher;
        this.maxRetry = Math.max(1, properties.getIndex().getMaxRetry());
    }

    @Override
    public void scheduleIndex(Long documentId) {
        processor.schedule(documentId);
    }

    /**
     * Fallback polling — runs every 5 minutes to catch any tasks missed by Pub/Sub.
     * If Pub/Sub is enabled, only pick up stale tasks to avoid racing with Pub/Sub delivery.
     */
    @Override
    @Scheduled(fixedDelay = 300_000)
    public void pollPendingTasks() {
        List<IndexTask> tasks = indexEventPublisher.isEnabled()
                ? indexTaskMapper.findStalePending(5, LocalDateTime.now().minusMinutes(2))
                : indexTaskMapper.findPending(5);
        for (IndexTask task : tasks) {
            processor.schedule(task.getDocumentId());
        }

        List<IndexTask> retryable = indexTaskMapper.findRetryable(maxRetry, 5);
        for (IndexTask task : retryable) {
            int retryCount = task.getRetryCount() != null ? task.getRetryCount() : 0;
            int backoffMinutes = retryCount > 0 && retryCount <= RETRY_BACKOFF_MINUTES.length
                    ? RETRY_BACKOFF_MINUTES[retryCount - 1]
                    : RETRY_BACKOFF_MINUTES[RETRY_BACKOFF_MINUTES.length - 1];
            LocalDateTime minUpdatedAt = LocalDateTime.now().minusMinutes(backoffMinutes);
            if (task.getUpdatedAt() != null && task.getUpdatedAt().isAfter(minUpdatedAt)) {
                continue;
            }
            task.setStatus("PENDING");
            indexTaskMapper.updateById(task);
            processor.schedule(task.getDocumentId());
        }
    }

    /**
     * 直接调用（不经过锁），供 {@code DocumentService.reindex} 等同步路径使用。
     * 调用方需自行确保锁（如有需要）。
     */
    @Override
    public void indexDocument(Long documentId) {
        KbDocument doc = documentMapper.selectById(documentId);
        if (doc == null) {
            return;
        }
        processor.execute(documentId);
    }
}
