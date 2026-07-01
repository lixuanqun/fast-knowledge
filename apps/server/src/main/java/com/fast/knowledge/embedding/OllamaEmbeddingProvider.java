package com.fast.knowledge.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.security.ExternalAccessGuard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(name = "knowledge.embedding.provider", havingValue = "ollama")
public class OllamaEmbeddingProvider implements EmbeddingProvider {

    private final KnowledgeProperties properties;
    private final String baseUrl;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OllamaEmbeddingProvider(KnowledgeProperties properties, ExternalAccessGuard externalAccessGuard) {
        this.properties = properties;
        this.baseUrl = OllamaUrlHelper.toNativeBaseUrl(properties.getEmbedding().getOllamaUrl());
        externalAccessGuard.validateEmbeddingEndpoint(baseUrl);
        log.info("Ollama Embedding 服务地址: {}", baseUrl);
    }

    @Override
    public float[] embed(String text) {
        return embedBatch(List.of(text)).get(0);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> result = new ArrayList<>();
        for (String text : texts) {
            try {
                Map<String, Object> body = new HashMap<>();
                body.put("model", properties.getEmbedding().getOllamaModel());
                body.put("prompt", text);
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                String url = baseUrl + "/api/embeddings";
                String response = restTemplate.postForObject(url, new HttpEntity<>(body, headers), String.class);
                JsonNode node = objectMapper.readTree(response);
                JsonNode embedding = node.get("embedding");
                float[] vec = new float[embedding.size()];
                for (int i = 0; i < embedding.size(); i++) {
                    vec[i] = (float) embedding.get(i).asDouble();
                }
                result.add(vec);
            } catch (Exception e) {
                log.error("Ollama embedding 失败: {}", e.getMessage());
                throw new RuntimeException("Embedding 失败", e);
            }
        }
        return result;
    }

    @Override
    public int dimension() {
        return properties.getEmbedding().getDimension();
    }
}
