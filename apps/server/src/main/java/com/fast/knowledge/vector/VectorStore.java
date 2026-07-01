package com.fast.knowledge.vector;

import java.io.IOException;
import java.util.List;

public interface VectorStore {

    void addChunks(Long kbId, List<VectorChunk> chunks) throws IOException;

    void deleteByDocument(Long kbId, Long docId) throws IOException;

    void deleteChunk(Long kbId, Long chunkId) throws IOException;

    void deleteKb(Long kbId) throws IOException;

    List<SearchHit> hybridSearch(Long kbId, String queryText, float[] queryVector, int topK, double alpha)
            throws IOException;
}
