package com.fast.knowledge.config;

import com.fast.knowledge.service.DocumentIngestService;
import com.fast.knowledge.service.IndexEventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Redis Pub/Sub 配置 — 仅在 redis 缓存提供方激活且索引事件驱动模式开启时生效。
 */
@Configuration
@ConditionalOnProperty(name = "knowledge.cache.provider", havingValue = "redis", matchIfMissing = true)
public class RedisPubSubConfig {

    @Bean
    @ConditionalOnProperty(name = "knowledge.index.pubsub-enabled", havingValue = "true", matchIfMissing = true)
    public ChannelTopic indexTopic() {
        return new ChannelTopic(IndexEventPublisher.INDEX_CHANNEL);
    }

    @Bean
    @ConditionalOnProperty(name = "knowledge.index.pubsub-enabled", havingValue = "true", matchIfMissing = true)
    public IndexMessageListener indexMessageListener(DocumentIngestService documentIngestService) {
        return new IndexMessageListener(documentIngestService);
    }

    @Bean
    @ConditionalOnProperty(name = "knowledge.index.pubsub-enabled", havingValue = "true", matchIfMissing = true)
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            IndexMessageListener indexMessageListener,
            ChannelTopic indexTopic) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(indexMessageListener, indexTopic);
        return container;
    }
}
