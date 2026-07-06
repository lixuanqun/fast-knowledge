package com.fast.knowledge.embedding;

import dev.langchain4j.model.embedding.EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 应用启动后对 Embedding 模型做一次预热推理，消除首次查询的冷启动延迟。
 *
 * <p>ONNX 模型首次推理需要加载计算图、分配 GPU/CPU 缓冲，耗时 ~100-300ms。
 * 预热后首次用户查询可直接命中热路径。
 */
@Component
public class EmbeddingWarmup {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingWarmup.class);

    private static final String WARMUP_TEXT = "Fast Knowledge 知识库系统预热";

    private final EmbeddingModel embeddingModel;

    public EmbeddingWarmup(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmup() {
        long start = System.currentTimeMillis();
        try {
            var embedding = embeddingModel.embed(WARMUP_TEXT).content();
            long elapsed = System.currentTimeMillis() - start;
            log.info("Embedding 模型预热完成: dim={}, latency={}ms",
                    embedding.vectorAsList().size(), elapsed);
        } catch (Exception e) {
            log.warn("Embedding 模型预热失败（不影响正常使用）: {}", e.getMessage());
        }
    }
}
