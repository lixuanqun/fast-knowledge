package com.fast.knowledge.graph;

import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.model.entity.KgEdge;
import com.fast.knowledge.model.entity.KgEntity;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 默认图存储 — 全文件模式（LightRAG 默认栈同型）：per-KB 内存图视图 + JSON 落盘。
 *
 * <p>节点按名称去重、边按 (src,dst,relation) 去重，语义与 MySQL 邻接表实现完全一致；
 * 每 KB 一个 JSON 文件（data/graphs/kb-{id}.json），3 秒延迟落盘，关闭/重建时强制提交。
 * 图是可重建的派生索引：文件损坏直接删除后经管理端"重建索引"重灌即可。
 * 边数超过数万（单 KB >50 万为 ADR 切换信号）时请评估切换 mysql/TuGraph 引擎。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "knowledge.kg.storage", havingValue = "file", matchIfMissing = true)
public class FileGraphStore implements GraphStore {

    private static final ScheduledExecutorService FLUSHER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "kg-graph-flusher");
        t.setDaemon(true);
        return t;
    });

    private static final long FLUSH_DELAY_MS = 3000;

    private final Path baseDir;
    private final ObjectMapper objectMapper;
    private final Map<Long, KbGraph> graphs = new ConcurrentHashMap<>();
    private final Map<Long, ScheduledFuture<?>> pendingFlushes = new ConcurrentHashMap<>();

    public FileGraphStore(KnowledgeProperties properties) {
        this.baseDir = Path.of(properties.getKg().getStorageDir());
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                // KbGraph 为字段直存载体（无 getter），开启字段可见性
                .setVisibility(com.fasterxml.jackson.annotation.PropertyAccessor.FIELD,
                        com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY);
        log.info("图存储引擎: file（per-KB JSON）目录={}", baseDir);
    }

    private KbGraph graph(Long kbId) {
        return graphs.computeIfAbsent(kbId, this::load);
    }

    private synchronized KbGraph load(Long kbId) {
        Path file = fileOf(kbId);
        KbGraph graph = new KbGraph();
        if (Files.exists(file)) {
            try {
                KbGraph loaded = objectMapper.readValue(file.toFile(), KbGraph.class);
                if (loaded != null) {
                    graph = loaded;
                }
                log.info("知识图谱已加载: file={} ({} 实体 / {} 边)", file,
                        graph.entities.size(), graph.edges.size());
            } catch (IOException e) {
                log.warn("知识图谱加载失败，将重建空图（可在管理端重建索引重灌）: file={}, error={}",
                        file, e.getMessage());
            }
        }
        return graph;
    }

    private Path fileOf(Long kbId) {
        return baseDir.resolve("kb-" + kbId + ".json");
    }

    @Override
    public KgEntity findEntity(Long kbId, String name) {
        return graph(kbId).entities.get(name);
    }

    @Override
    public KgEntity entityById(Long id) {
        for (KbGraph graph : graphs.values()) {
            for (KgEntity entity : graph.entities.values()) {
                if (entity.getId().equals(id)) {
                    return entity;
                }
            }
        }
        return null;
    }

    @Override
    public synchronized void insertEntity(KgEntity entity) {
        KbGraph graph = graph(entity.getKbId());
        entity.setId(++graph.entitySeq);
        entity.setCreatedAt(LocalDateTime.now());
        graph.entities.put(entity.getName(), entity);
        markDirty(entity.getKbId());
    }

    @Override
    public synchronized boolean edgeExists(Long kbId, Long srcId, Long dstId, String relation) {
        return graph(kbId).edges.containsKey(edgeKey(srcId, dstId, relation));
    }

    @Override
    public synchronized void insertEdge(KgEdge edge) {
        KbGraph graph = graph(edge.getKbId());
        edge.setId(++graph.edgeSeq);
        edge.setCreatedAt(LocalDateTime.now());
        graph.edges.put(edgeKey(edge.getSrcId(), edge.getDstId(), edge.getRelation()), edge);
        markDirty(edge.getKbId());
    }

    @Override
    public synchronized void deleteEdgesByDocument(Long kbId, Long docId) {
        boolean removed = graph(kbId).edges.values()
                .removeIf(edge -> docId.equals(edge.getEvidenceDocId()));
        if (removed) {
            markDirty(kbId);
        }
    }

    @Override
    public synchronized void deleteByKb(Long kbId) {
        KbGraph graph = graphs.remove(kbId);
        if (graph != null && (!graph.entities.isEmpty() || !graph.edges.isEmpty())) {
            try {
                Files.deleteIfExists(fileOf(kbId));
            } catch (IOException e) {
                log.warn("知识图谱文件删除失败 kbId={}: {}", kbId, e.getMessage());
            }
        }
    }

    @Override
    public List<KgEntity> entities(Long kbId) {
        return new ArrayList<>(graph(kbId).entities.values());
    }

    @Override
    public List<KgEdge> edges(Long kbId) {
        return new ArrayList<>(graph(kbId).edges.values());
    }

    private static String edgeKey(Long srcId, Long dstId, String relation) {
        return srcId + "|" + dstId + "|" + relation;
    }

    private void markDirty(Long kbId) {
        ScheduledFuture<?> pending = pendingFlushes.get(kbId);
        if (pending == null || pending.isDone()) {
            pendingFlushes.put(kbId, FLUSHER.schedule(() -> flush(kbId), FLUSH_DELAY_MS, TimeUnit.MILLISECONDS));
        }
    }

    /** 强制落盘单个知识库（重建流程结束时调用方无须感知，延迟任务兜底） */
    public synchronized void flush(Long kbId) {
        KbGraph graph = graphs.get(kbId);
        if (graph == null) {
            return;
        }
        try {
            Files.createDirectories(baseDir);
            Path tmp = fileOf(kbId).resolveSibling(fileOf(kbId).getFileName() + ".tmp");
            Files.writeString(tmp, objectMapper.writeValueAsString(graph));
            Files.move(tmp, fileOf(kbId),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            log.debug("知识图谱已落盘: kbId={} ({} 实体 / {} 边)", kbId, graph.entities.size(), graph.edges.size());
        } catch (IOException e) {
            throw new IllegalStateException("知识图谱落盘失败 kbId=" + kbId, e);
        }
    }

    /** 全量落盘（应用关闭时） */
    @jakarta.annotation.PreDestroy
    public void flushAll() {
        for (Long kbId : graphs.keySet()) {
            try {
                flush(kbId);
            } catch (Exception e) {
                log.warn("知识图谱关闭落盘失败 kbId={}: {}", kbId, e.getMessage());
            }
        }
    }

    /** per-KB 图数据（Jackson 序列化载体） */
    static class KbGraph {
        LinkedHashMap<String, KgEntity> entities = new LinkedHashMap<>();
        LinkedHashMap<String, KgEdge> edges = new LinkedHashMap<>();
        long entitySeq;
        long edgeSeq;
    }
}
