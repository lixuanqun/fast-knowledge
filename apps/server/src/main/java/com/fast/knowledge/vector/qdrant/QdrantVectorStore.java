package com.fast.knowledge.vector.qdrant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.vector.SearchHit;
import com.fast.knowledge.vector.VectorChunk;
import com.fast.knowledge.vector.VectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(name = "knowledge.vector.provider", havingValue = "qdrant")
public class QdrantVectorStore implements VectorStore {

    private final KnowledgeProperties properties;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public QdrantVectorStore(KnowledgeProperties properties) {
        this.properties = properties;
        ensureCollection();
        log.info("Qdrant 向量存储已启用: {}", baseUrl());
    }

    private String baseUrl() {
        var q = properties.getVector().getQdrant();
        return "http://" + q.getHost() + ":" + q.getPort();
    }

    private String collectionName(Long kbId) {
        return "kb_" + kbId;
    }

    private void ensureCollection() {
        // collections are created per KB on first write
    }

    private void ensureKbCollection(Long kbId) {
        String name = collectionName(kbId);
        String url = baseUrl() + "/collections/" + name;
        try {
            restTemplate.getForEntity(url, String.class);
        } catch (Exception e) {
            ObjectNode body = objectMapper.createObjectNode();
            ObjectNode vectors = body.putObject("vectors");
            vectors.put("size", properties.getEmbedding().getDimension());
            vectors.put("distance", "Cosine");
            restTemplate.put(baseUrl() + "/collections/" + name, body);
        }
    }

    @Override
    public void addChunks(Long kbId, List<VectorChunk> chunks) throws IOException {
        ensureKbCollection(kbId);
        String url = baseUrl() + "/collections/" + collectionName(kbId) + "/points?wait=true";
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode points = body.putArray("points");
        for (VectorChunk chunk : chunks) {
            ObjectNode point = points.addObject();
            point.put("id", chunk.getChunkId());
            ArrayNode vector = point.putArray("vector");
            for (float v : chunk.getVector()) {
                vector.add(v);
            }
            ObjectNode payload = point.putObject("payload");
            payload.put("kbId", kbId);
            payload.put("docId", chunk.getDocId());
            payload.put("chunkId", chunk.getChunkId());
            payload.put("title", chunk.getTitle() != null ? chunk.getTitle() : "");
            payload.put("content", chunk.getContent());
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
    }

    @Override
    public void deleteByDocument(Long kbId, Long docId) throws IOException {
        String url = baseUrl() + "/collections/" + collectionName(kbId) + "/points/delete?wait=true";
        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode filter = body.putObject("filter");
        ArrayNode must = filter.putArray("must");
        ObjectNode match = must.addObject();
        match.putObject("match").put("key", "docId").put("value", docId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
        } catch (Exception ignored) {
            // collection may not exist
        }
    }

    @Override
    public void deleteChunk(Long kbId, Long chunkId) throws IOException {
        String url = baseUrl() + "/collections/" + collectionName(kbId) + "/points/delete?wait=true";
        ObjectNode body = objectMapper.createObjectNode();
        body.putArray("points").add(chunkId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
    }

    @Override
    public void deleteKb(Long kbId) throws IOException {
        try {
            restTemplate.delete(baseUrl() + "/collections/" + collectionName(kbId));
        } catch (Exception ignored) {
            // collection may not exist
        }
    }

    @Override
    public List<SearchHit> hybridSearch(Long kbId, String queryText, float[] queryVector,
                                        int topK, double alpha) throws IOException {
        String url = baseUrl() + "/collections/" + collectionName(kbId) + "/points/search";
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode vector = body.putArray("vector");
        for (float v : queryVector) {
            vector.add(v);
        }
        body.put("limit", topK);
        body.put("with_payload", true);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            var response = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), JsonNode.class);
            List<SearchHit> hits = new ArrayList<>();
            if (response.getBody() != null && response.getBody().has("result")) {
                for (JsonNode node : response.getBody().get("result")) {
                    JsonNode payload = node.get("payload");
                    SearchHit hit = new SearchHit();
                    hit.setKbId(kbId);
                    hit.setChunkId(payload.get("chunkId").asLong());
                    hit.setDocumentId(payload.get("docId").asLong());
                    hit.setTitle(payload.path("title").asText(""));
                    hit.setContent(payload.path("content").asText(""));
                    hit.setScore(node.path("score").asDouble(0) * alpha);
                    hits.add(hit);
                }
            }
            return hits;
        } catch (Exception e) {
            throw new IOException("Qdrant 检索失败: " + e.getMessage(), e);
        }
    }
}
