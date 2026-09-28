package com.fast.knowledge.langchain4j.store;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WP-Vec Lucene 向量索引引擎 — 进程内 HNSW（纯 Java，零中间件），段文件落盘。
 *
 * <p>替代 LocalEmbeddingStore（内存暴力扫描 + JSON）的 ANN 升级形态，接渗与兼容约定：
 * <ul>
 *   <li>分数语义与 langchain4j 内存实现完全一致：余弦相似度归一化到 [0,1]（Lucene COSINE
 *       与 RelevanceScore 同为 (1+cos)/2），下游融合/UI/阈值无需调整；</li>
 *   <li>过滤：翻译 langchain4j Filter 的 IsEqualTo / And / Or / Not 到 Lucene Query
 *       （keyword 字段精确匹配），HNSW 预过滤检索；其余过滤形态不受支持；</li>
 *   <li>落盘：3 秒延迟 commit（同 LocalEmbeddingStore 的 flush 节奏），关闭/逐出时强制提交；
 *       向量本体只进 HNSW 图不进 stored 字段，查询返回的 Embedding 为 null（下游仅用
 *       元数据与原文）；</li>
 *   <li>重建：换引擎或模型维度变化后，旧索引不可读，经管理端"重建索引"重灌。</li>
 * </ul>
 */
@Slf4j
public class LuceneEmbeddingStore implements EmbeddingStore<TextSegment>, VectorIndexLifecycle {

    public static final String FIELD_ID = "_id";
    public static final String FIELD_VECTOR = "vector";
    public static final String FIELD_TEXT = "_text";
    public static final String FIELD_META_PREFIX = "meta_";

    private static final ScheduledExecutorService FLUSHER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "lucene-vector-flusher");
        t.setDaemon(true);
        return t;
    });

    private static final long COMMIT_DELAY_MS = 3000;

    private final Path directory;
    private final IndexWriter writer;
    private final SearcherManager searcherManager;
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private ScheduledFuture<?> pendingCommit;
    private boolean closed;

    private LuceneEmbeddingStore(Path directory, IndexWriter writer, SearcherManager searcherManager) {
        this.directory = directory;
        this.writer = writer;
        this.searcherManager = searcherManager;
    }

    /** 打开（或创建）per-KB 的 Lucene 索引目录 */
    public static LuceneEmbeddingStore open(Path dir) {
        try {
            java.nio.file.Files.createDirectories(dir);
            Directory directory = new MMapDirectory(dir);
            IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig());
            SearcherManager searcherManager = new SearcherManager(writer, null);
            int existingDocs = writer.getDocStats().numDocs;
            log.info("Lucene 向量索引已打开: dir={} ({} docs)", dir, existingDocs);
            if (existingDocs == 0) {
                writer.commit();
            }
            return new LuceneEmbeddingStore(dir, writer, searcherManager);
        } catch (IOException e) {
            throw new IllegalStateException("Lucene 向量索引打开失败: " + dir, e);
        }
    }

    @Override
    public String add(Embedding embedding) {
        throw new UnsupportedOperationException("请使用 add(id, embedding, segment) 携带元数据");
    }

    @Override
    public void add(String id, Embedding embedding) {
        throw new UnsupportedOperationException("请使用 add(id, embedding, segment) 携带元数据");
    }

    @Override
    public String add(Embedding embedding, TextSegment segment) {
        throw new UnsupportedOperationException("请使用 addAll(ids, embeddings, segments)");
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        throw new UnsupportedOperationException("请使用 addAll(ids, embeddings, segments)");
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        throw new UnsupportedOperationException("请使用 addAll(ids, embeddings, segments)");
    }

    @Override
    public synchronized void addAll(List<String> ids, List<Embedding> embeddings,
                                    List<TextSegment> segments) {
        if (embeddings.size() != segments.size() || ids.size() != segments.size()) {
            throw new IllegalArgumentException("ids、embeddings 与 segments 数量不一致");
        }
        try {
            for (int i = 0; i < ids.size(); i++) {
                writer.addDocument(toDocument(ids.get(i), embeddings.get(i), segments.get(i)));
            }
            markDirty();
        } catch (IOException e) {
            throw new IllegalStateException("Lucene 向量写入失败", e);
        }
    }

    @Override
    public synchronized void remove(String id) {
        try {
            writer.deleteDocuments(new Term(FIELD_ID, id));
            markDirty();
        } catch (IOException e) {
            throw new IllegalStateException("Lucene 向量删除失败", e);
        }
    }

    @Override
    public synchronized void removeAll(Collection<String> ids) {
        try {
            for (String id : ids) {
                writer.deleteDocuments(new Term(FIELD_ID, id));
            }
            markDirty();
        } catch (IOException e) {
            throw new IllegalStateException("Lucene 向量删除失败", e);
        }
    }

    @Override
    public synchronized void removeAll(Filter filter) {
        try {
            writer.deleteDocuments(toQuery(filter));
            markDirty();
        } catch (IOException e) {
            throw new IllegalStateException("Lucene 按过滤条件删除失败", e);
        }
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        float[] queryVector = request.queryEmbedding().vector();
        Query luceneQuery = new KnnFloatVectorQuery(FIELD_VECTOR, queryVector.clone(),
                Math.max(1, request.maxResults()), toQuery(request.filter()));
        try {
            IndexSearcher searcher = acquireSearcher();
            try {
                TopDocs topDocs = searcher.search(luceneQuery, Math.max(1, request.maxResults()));
                List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();
                for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                    if (request.minScore() > 0 && scoreDoc.score < request.minScore()) {
                        continue;
                    }
                    Document doc = searcher.storedFields().document(scoreDoc.doc);
                    matches.add(toMatch(doc, scoreDoc.score));
                }
                return new EmbeddingSearchResult<>(matches);
            } finally {
                searcherManager.release(searcher);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Lucene 向量检索失败", e);
        }
    }

    private IndexSearcher acquireSearcher() throws IOException {
        searcherManager.maybeRefreshBlocking();
        return searcherManager.acquire();
    }

    private EmbeddingMatch<TextSegment> toMatch(Document doc, double score) {
        Metadata metadata = new Metadata();
        for (org.apache.lucene.index.IndexableField field : doc.getFields()) {
            String name = field.name();
            if (!name.startsWith(FIELD_META_PREFIX)) {
                continue;
            }
            String key = name.substring(FIELD_META_PREFIX.length());
            restoreMetadata(metadata, key, doc.get(name));
        }
        String id = doc.get(FIELD_ID);
        TextSegment segment = TextSegment.from(doc.get(FIELD_TEXT), metadata);
        return new EmbeddingMatch<>(score, id, null, segment);
    }

    /** 写入侧带类型前缀存储，读回还原原始类型（下游 metadata.getLong/getInteger 依赖类型保真） */
    private static void restoreMetadata(Metadata metadata, String key, String stored) {
        if (stored == null || stored.length() < 2 || stored.charAt(1) != ':') {
            metadata.put(key, stored);
            return;
        }
        String tag = stored.substring(0, 1);
        String value = stored.substring(2);
        try {
            switch (tag) {
                case "l" -> metadata.put(key, Long.parseLong(value));
                case "i" -> metadata.put(key, Integer.parseInt(value));
                case "d" -> metadata.put(key, Double.parseDouble(value));
                default -> metadata.put(key, value);
            }
        } catch (NumberFormatException e) {
            metadata.put(key, value);
        }
    }

    private static String typeTagged(Object value) {
        if (value instanceof Long) {
            return "l:" + value;
        }
        if (value instanceof Integer) {
            return "i:" + value;
        }
        if (value instanceof Double) {
            return "d:" + value;
        }
        return "s:" + value;
    }

    private Document toDocument(String id, Embedding embedding, TextSegment segment) {
        Document doc = new Document();
        doc.add(new StringField(FIELD_ID, id, Field.Store.YES));
        doc.add(new StoredField(FIELD_TEXT, segment.text()));
        doc.add(new KnnFloatVectorField(FIELD_VECTOR, embedding.vector().clone()));
        Metadata metadata = segment.metadata();
        if (metadata != null) {
            for (java.util.Map.Entry<String, Object> entry : metadata.toMap().entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                // 过滤等值匹配按字符串比对（Long/Integer 统一十进制串），存储带类型前缀
                doc.add(new StringField(FIELD_META_PREFIX + entry.getKey(),
                        String.valueOf(entry.getValue()), Field.Store.YES));
                doc.add(new StoredField(FIELD_META_PREFIX + entry.getKey(), typeTagged(entry.getValue())));
            }
        }
        return doc;
    }

    /** langchain4j Filter → Lucene Query（仅支持 IsEqualTo / And / Or / Not；null 视为全量） */
    Query toQuery(Filter filter) {
        if (filter == null) {
            return new org.apache.lucene.search.MatchAllDocsQuery();
        }
        if (filter instanceof dev.langchain4j.store.embedding.filter.comparison.IsEqualTo isEqualTo) {
            String key = isEqualTo.key();
            String value = String.valueOf(isEqualTo.comparisonValue());
            return new TermQuery(new Term(FIELD_META_PREFIX + key, value));
        }
        if (filter instanceof dev.langchain4j.store.embedding.filter.logical.And and) {
            BooleanQuery.Builder builder = new BooleanQuery.Builder();
            builder.add(toQuery(and.left()), BooleanClause.Occur.MUST);
            builder.add(toQuery(and.right()), BooleanClause.Occur.MUST);
            return builder.build();
        }
        if (filter instanceof dev.langchain4j.store.embedding.filter.logical.Or or) {
            BooleanQuery.Builder builder = new BooleanQuery.Builder();
            builder.add(toQuery(or.left()), BooleanClause.Occur.SHOULD);
            builder.add(toQuery(or.right()), BooleanClause.Occur.SHOULD);
            return builder.build();
        }
        if (filter instanceof dev.langchain4j.store.embedding.filter.logical.Not not) {
            BooleanQuery.Builder builder = new BooleanQuery.Builder();
            builder.add(new org.apache.lucene.search.MatchAllDocsQuery(), BooleanClause.Occur.MUST);
            builder.add(toQuery(not.expression()), BooleanClause.Occur.MUST_NOT);
            return builder.build();
        }
        throw new UnsupportedOperationException("不支持的向量过滤形态: " + filter.getClass().getName());
    }

    private void markDirty() {
        dirty.set(true);
        if (pendingCommit == null || pendingCommit.isDone()) {
            pendingCommit = FLUSHER.schedule(this::flushQuietly, COMMIT_DELAY_MS, TimeUnit.MILLISECONDS);
        }
    }

    private void flushQuietly() {
        try {
            flush();
        } catch (Exception e) {
            log.warn("Lucene 延迟提交失败 dir={}: {}", directory, e.getMessage());
        }
    }

    @Override
    public synchronized void flush() {
        if (closed || !dirty.compareAndSet(true, false)) {
            return;
        }
        try {
            writer.commit();
            searcherManager.maybeRefreshBlocking();
            log.debug("Lucene 向量索引已提交: dir={}", directory);
        } catch (IOException e) {
            dirty.set(true);
            throw new IllegalStateException("Lucene 提交失败: " + directory, e);
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            if (dirty.get()) {
                writer.commit();
            }
            writer.close();
            searcherManager.close();
        } catch (IOException e) {
            log.warn("Lucene 向量索引关闭异常 dir={}: {}", directory, e.getMessage());
        }
    }
}
