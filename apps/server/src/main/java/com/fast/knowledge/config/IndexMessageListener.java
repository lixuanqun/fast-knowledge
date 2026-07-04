package com.fast.knowledge.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fast.knowledge.service.DocumentIngestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

import java.io.IOException;
import java.util.Map;

/**
 * 监听 Redis Pub/Sub 索引事件，触发文档索引。
 */
public class IndexMessageListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(IndexMessageListener.class);

    private final DocumentIngestService documentIngestService;
    private final ObjectMapper objectMapper;

    public IndexMessageListener(DocumentIngestService documentIngestService) {
        this.documentIngestService = documentIngestService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(message.getBody(), Map.class);
            Object docIdObj = payload.get("documentId");
            if (docIdObj == null) {
                log.warn("Received index event without documentId: {}", new String(message.getBody()));
                return;
            }
            Long documentId = docIdObj instanceof Integer
                    ? ((Integer) docIdObj).longValue()
                    : (Long) docIdObj;
            log.debug("Received index event for docId={}", documentId);
            documentIngestService.scheduleIndex(documentId);
        } catch (IOException e) {
            log.error("Failed to deserialize index event: {}", new String(message.getBody()), e);
        } catch (Exception e) {
            log.error("Failed to process index event", e);
        }
    }
}
