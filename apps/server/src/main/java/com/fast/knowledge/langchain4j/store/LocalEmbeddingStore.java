package com.fast.knowledge.langchain4j.store;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 本地向量索引（Classic 形态，MySQL 5.7 部署默认）：
 * 内存余弦检索 + per-KB JSON 文件持久化，无需任何向量数据库。
 * <p>规模定位：万级文档 / 数十万块以内。写入采用 3 秒防抖异步落盘，
 * 逐出与停机时同步 flush 保证持久化。
 */
@Slf4j
public class LocalEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final ScheduledExecutorService FLUSHER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "local-vector-flusher");
        t.setDaemon(true);
        return t;
    });

    private static final long FLUSH_DELAY_MS = 3000;

    private final Path file;
    private final InMemoryEmbeddingStore<TextSegment> delegate;
    private boolean dirty;
    private ScheduledFuture<?> pendingFlush;

    private LocalEmbeddingStore(Path file, InMemoryEmbeddingStore<TextSegment> delegate) {
        this.file = file;
        this.delegate = delegate;
    }

    /** 从磁盘加载；文件不存在时返回空库 */
    public static LocalEmbeddingStore load(Path file) {
        if (Files.exists(file)) {
            try {
                InMemoryEmbeddingStore<TextSegment> delegate = InMemoryEmbeddingStore.fromJson(Files.readString(file));
                log.info("本地向量索引已加载: file={}", file);
                return new LocalEmbeddingStore(file, delegate);
            } catch (IOException e) {
                log.warn("本地向量索引加载失败，将重建空库: file={}, error={}", file, e.getMessage());
            }
        }
        return new LocalEmbeddingStore(file, new InMemoryEmbeddingStore<>());
    }

    @Override
    public synchronized String add(Embedding embedding) {
        String id = delegate.add(embedding);
        markDirty();
        return id;
    }

    @Override
    public synchronized void add(String id, Embedding embedding) {
        delegate.add(id, embedding);
        markDirty();
    }

    @Override
    public synchronized String add(Embedding embedding, TextSegment segment) {
        String id = delegate.add(embedding, segment);
        markDirty();
        return id;
    }

    @Override
    public synchronized List<String> addAll(List<Embedding> embeddings) {
        List<String> ids = delegate.addAll(embeddings);
        markDirty();
        return ids;
    }

    @Override
    public synchronized List<String> addAll(List<Embedding> embeddings, List<TextSegment> segments) {
        List<String> ids = delegate.addAll(embeddings, segments);
        markDirty();
        return ids;
    }

    @Override
    public synchronized void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> segments) {
        delegate.addAll(ids, embeddings, segments);
        markDirty();
    }

    @Override
    public synchronized void remove(String id) {
        delegate.remove(id);
        markDirty();
    }

    @Override
    public synchronized void removeAll(Collection<String> ids) {
        delegate.removeAll(ids);
        markDirty();
    }

    @Override
    public synchronized void removeAll(Filter filter) {
        delegate.removeAll(filter);
        markDirty();
    }

    @Override
    public synchronized EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        return delegate.search(request);
    }

    /** 立即落盘（若脏）；幂等 */
    public synchronized void flush() {
        if (!dirty) {
            return;
        }
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, delegate.serializeToJson());
            dirty = false;
            log.debug("本地向量索引已落盘: {}", file);
        } catch (IOException e) {
            throw new UncheckedIOException("本地向量索引落盘失败: " + file, e);
        }
    }

    private void markDirty() {
        dirty = true;
        if (pendingFlush != null && !pendingFlush.isDone()) {
            pendingFlush.cancel(false);
        }
        pendingFlush = FLUSHER.schedule(this::flush, FLUSH_DELAY_MS, TimeUnit.MILLISECONDS);
    }
}
