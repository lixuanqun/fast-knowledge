package com.fast.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "knowledge")
public class KnowledgeProperties {

    private Jwt jwt = new Jwt();
    private Storage storage = new Storage();
    private Lucene lucene = new Lucene();
    private Vector vector = new Vector();
    private Cache cache = new Cache();
    private Embedding embedding = new Embedding();
    private Chunk chunk = new Chunk();
    private Search search = new Search();
    private Llm llm = new Llm();
    private Cors cors = new Cors();
    private Setup setup = new Setup();

    @Data
    public static class Vector {
        private String provider = "lucene";
        private PgVector pgvector = new PgVector();
        private Qdrant qdrant = new Qdrant();
    }

    @Data
    public static class PgVector {
        private String datasourceUrl = "jdbc:postgresql://localhost:5432/fast_knowledge";
        private String username = "postgres";
        private String password = "postgres";
    }

    @Data
    public static class Qdrant {
        private String host = "localhost";
        private int port = 6333;
    }

    @Data
    public static class Cache {
        private String provider = "redis";
    }

    @Data
    public static class Cors {
        private String allowedOrigins = "*";
    }

    @Data
    public static class Setup {
        private String instanceName = "Fast Knowledge";
    }

    @Data
    public static class Jwt {
        private String secret;
        private long expireSeconds = 86400;
    }

    @Data
    public static class Storage {
        private String uploadDir = "./data/uploads";
    }

    @Data
    public static class Lucene {
        private String basePath = "./data/lucene";
    }

    @Data
    public static class Embedding {
        private String provider = "onnx";
        private String onnxModelPath = "./models/bge-small-zh-v1.5.onnx";
        private String onnxTokenizerPath = "./models/tokenizer.json";
        private int dimension = 512;
        private int onnxMaxSeqLen = 512;
        private int onnxBatchSize = 16;
        private String ollamaUrl = "http://localhost:11434";
        private String ollamaModel = "nomic-embed-text";
    }

    @Data
    public static class Chunk {
        private int size = 512;
        private int overlap = 50;
    }

    @Data
    public static class Search {
        private int defaultTopK = 8;
        private double hybridAlpha = 0.6;
    }

    @Data
    public static class Llm {
        /** 预设提供商: ollama | deepseek | glm | dashscope | volcengine | openai | custom */
        private String provider = "ollama";
        private String baseUrl = "";
        private String apiKey = "";
        private String model = "";
        private int maxTokens = 4096;
        private double temperature = 0.3;
        private boolean allowExternal = true;
    }
}
