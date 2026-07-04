package com.fast.knowledge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 发布索引事件到 Redis Pub/Sub，触发异步文档索引。
 */
@Component
public class IndexEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(IndexEventPublisher.class);
    public static final String INDEX_CHANNEL = "kb:index:queue";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public IndexEventPublisher(StringRedisTemplate redisTemplate,
                               com.fast.knowledge.config.KnowledgeProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.enabled = properties.getIndex().isPubsubEnabled();
    }

    /**
     * 发布文档索引事件。若 Pub/Sub 被禁用，静默跳过（由轮询兜底）。
     */
    public void publish(Long documentId) {
        if (!enabled) {
            return;
        }
        try {
            String message = objectMapper.writeValueAsString(Map.of("documentId", documentId));
            redisTemplate.convertAndSend(INDEX_CHANNEL, message);
            log.debug("Published index event for docId={}", documentId);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize index event for docId={}", documentId, e);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}
