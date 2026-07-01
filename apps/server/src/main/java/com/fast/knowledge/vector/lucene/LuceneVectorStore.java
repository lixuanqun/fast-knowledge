package com.fast.knowledge.vector.lucene;

import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.vector.SearchHit;
import com.fast.knowledge.vector.VectorChunk;
import com.fast.knowledge.vector.VectorStore;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.FSDirectory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class LuceneVectorStore implements VectorStore {

    public static final String FIELD_KB_ID = "kbId";
    public static final String FIELD_DOC_ID = "docId";
    public static final String FIELD_CHUNK_ID = "chunkId";
    public static final String FIELD_TITLE = "title";
    public static final String FIELD_CONTENT = "content";
    public static final String FIELD_VECTOR = "vector";

    private final KnowledgeProperties properties;
    private final Map<Long, IndexWriter> writers = new ConcurrentHashMap<>();
    private final Analyzer analyzer = new SmartChineseAnalyzer();

    public LuceneVectorStore(KnowledgeProperties properties) {
        this.properties = properties;
    }

    @Override
    public synchronized void addChunks(Long kbId, List<VectorChunk> chunks) throws IOException {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        IndexWriter writer = getWriter(kbId);
        for (VectorChunk chunk : chunks) {
            Document doc = new Document();
            doc.add(new StringField(FIELD_KB_ID, String.valueOf(kbId), Field.Store.YES));
            doc.add(new StringField(FIELD_DOC_ID, String.valueOf(chunk.getDocId()), Field.Store.YES));
            doc.add(new StringField(FIELD_CHUNK_ID, String.valueOf(chunk.getChunkId()), Field.Store.YES));
            doc.add(new TextField(FIELD_TITLE, chunk.getTitle() != null ? chunk.getTitle() : "", Field.Store.YES));
            doc.add(new TextField(FIELD_CONTENT, chunk.getContent(), Field.Store.YES));
            doc.add(new KnnFloatVectorField(FIELD_VECTOR, chunk.getVector(), VectorSimilarityFunction.COSINE));
            writer.addDocument(doc);
        }
        writer.commit();
    }

    @Override
    public synchronized void deleteByDocument(Long kbId, Long docId) throws IOException {
        IndexWriter writer = getWriter(kbId);
        writer.deleteDocuments(new Term(FIELD_DOC_ID, String.valueOf(docId)));
        writer.commit();
    }

    @Override
    public synchronized void deleteChunk(Long kbId, Long chunkId) throws IOException {
        IndexWriter writer = getWriter(kbId);
        writer.deleteDocuments(new Term(FIELD_CHUNK_ID, String.valueOf(chunkId)));
        writer.commit();
    }

    @Override
    public synchronized void deleteKb(Long kbId) throws IOException {
        closeWriter(kbId);
        Path path = kbPath(kbId);
        if (Files.exists(path)) {
            try (var walk = Files.walk(path)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }
    }

    @Override
    public List<SearchHit> hybridSearch(Long kbId, String queryText, float[] queryVector,
                                        int topK, double alpha) throws IOException {
        Path path = kbPath(kbId);
        if (!Files.exists(path)) {
            return List.of();
        }
        try (DirectoryReader reader = DirectoryReader.open(FSDirectory.open(path))) {
            IndexSearcher searcher = new IndexSearcher(reader);
            searcher.setSimilarity(new BM25Similarity());

            Query knnQuery = new KnnFloatVectorQuery(FIELD_VECTOR, queryVector, topK * 2);
            BooleanQuery.Builder filter = new BooleanQuery.Builder();
            filter.add(new TermQuery(new Term(FIELD_KB_ID, String.valueOf(kbId))), BooleanClause.Occur.FILTER);
            BooleanQuery.Builder knnWithFilter = new BooleanQuery.Builder();
            knnWithFilter.add(knnQuery, BooleanClause.Occur.MUST);
            knnWithFilter.add(filter.build(), BooleanClause.Occur.FILTER);

            TopDocs vectorHits = searcher.search(knnWithFilter.build(), topK * 2);

            org.apache.lucene.queryparser.classic.QueryParser parser =
                    new org.apache.lucene.queryparser.classic.QueryParser(FIELD_CONTENT, analyzer);
            Query textQuery;
            try {
                textQuery = parser.parse(org.apache.lucene.queryparser.classic.QueryParser.escape(
                        queryText != null ? queryText : ""));
            } catch (org.apache.lucene.queryparser.classic.ParseException e) {
                textQuery = new TermQuery(new Term(FIELD_CONTENT, queryText != null ? queryText : ""));
            }
            BooleanQuery.Builder textWithFilter = new BooleanQuery.Builder();
            textWithFilter.add(textQuery, BooleanClause.Occur.MUST);
            textWithFilter.add(filter.build(), BooleanClause.Occur.FILTER);
            TopDocs textHits = searcher.search(textWithFilter.build(), topK * 2);

            Map<Long, Double> scores = new HashMap<>();
            Map<Long, SearchHit> chunks = new HashMap<>();

            for (ScoreDoc sd : vectorHits.scoreDocs) {
                Document d = searcher.storedFields().document(sd.doc);
                Long chunkId = Long.parseLong(d.get(FIELD_CHUNK_ID));
                scores.merge(chunkId, (double) sd.score * alpha, Double::sum);
                chunks.put(chunkId, toSearchHit(d, 0));
            }
            for (ScoreDoc sd : textHits.scoreDocs) {
                Document d = searcher.storedFields().document(sd.doc);
                Long chunkId = Long.parseLong(d.get(FIELD_CHUNK_ID));
                scores.merge(chunkId, (double) sd.score * (1 - alpha), Double::sum);
                chunks.put(chunkId, toSearchHit(d, 0));
            }

            List<SearchHit> result = new ArrayList<>();
            scores.entrySet().stream()
                    .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                    .limit(topK)
                    .forEach(e -> {
                        SearchHit hit = chunks.get(e.getKey());
                        hit.setScore(e.getValue());
                        result.add(hit);
                    });
            return result;
        }
    }

    private SearchHit toSearchHit(Document d, double score) {
        SearchHit hit = new SearchHit();
        hit.setKbId(Long.parseLong(d.get(FIELD_KB_ID)));
        hit.setDocumentId(Long.parseLong(d.get(FIELD_DOC_ID)));
        hit.setChunkId(Long.parseLong(d.get(FIELD_CHUNK_ID)));
        hit.setTitle(d.get(FIELD_TITLE));
        hit.setContent(d.get(FIELD_CONTENT));
        hit.setScore(score);
        return hit;
    }

    private IndexWriter getWriter(Long kbId) throws IOException {
        return writers.computeIfAbsent(kbId, id -> {
            try {
                Path path = kbPath(id);
                Files.createDirectories(path);
                IndexWriterConfig config = new IndexWriterConfig(analyzer);
                return new IndexWriter(FSDirectory.open(path), config);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void closeWriter(Long kbId) throws IOException {
        IndexWriter writer = writers.remove(kbId);
        if (writer != null) {
            writer.close();
        }
    }

    private Path kbPath(Long kbId) {
        return Path.of(properties.getLucene().getBasePath(), String.valueOf(kbId));
    }
}
