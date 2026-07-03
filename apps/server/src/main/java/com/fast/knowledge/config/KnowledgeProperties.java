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
    private Vector vector = new Vector();
    private Cache cache = new Cache();
    private Embedding embedding = new Embedding();
    private Chunk chunk = new Chunk();
    private Search search = new Search();
    private Llm llm = new Llm();
    private Cors cors = new Cors();
    private Setup setup = new Setup();
    private Auth auth = new Auth();
    private Wiki wiki = new Wiki();

    @Data
    public static class Wiki {
        private boolean enabled = true;
        /** false 时 Wiki 页保持 DRAFT，需管理员审核 */
        private boolean autoPublish = false;
    }

    @Data
    public static class Auth {
        private Ldap ldap = new Ldap();
        private Oidc oidc = new Oidc();
    }

    @Data
    public static class Ldap {
        private boolean enabled = false;
        private String url = "";
        private String baseDn = "";
        /** 如 uid={0},ou=people,dc=example,dc=com */
        private String userDnPattern = "";
        private String userSearchBase = "";
        /** 如 (uid={0}) */
        private String userSearchFilter = "";
    }

    @Data
    public static class Oidc {
        private boolean enabled = false;
        private String issuerUri = "";
        private String clientId = "";
        private String clientSecret = "";
        /** 后端回调地址，如 http://localhost:8088/api/auth/oidc/callback */
        private String redirectUri = "";
        /** 登录成功后跳转前端，如 http://localhost:8088/login/callback */
        private String frontendRedirectUri = "";
        private String scope = "openid profile email";
    }

    @Data
    public static class Vector {
        private String provider = "pgvector";
        private PgVector pgvector = new PgVector();
    }

    @Data
    public static class PgVector {
        private String host = "localhost";
        private int port = 5432;
        private String database = "fast_knowledge";
        private String user = "postgres";
        private String password = "postgres";
        private String table = "kb_embeddings";
        private String searchMode = "HYBRID";
        private int rrfK = 60;
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
        private String provider = "minio";
        private Minio minio = new Minio();
    }

    @Data
    public static class Minio {
        private String endpoint = "";
        private String bucket = "";
        private String accessKey = "";
        private String secretKey = "";
        private String region = "";
        private String prefix = "";
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
        private int cacheTtlMinutes = 5;
        private Rerank rerank = new Rerank();
    }

    @Data
    public static class Rerank {
        /** 是否启用检索重排序 */
        private boolean enabled = false;
        /** cohere | jina | onnx */
        private String provider = "cohere";
        /** 初召回倍数：先取 topK * multiplier，再 rerank 截断 */
        private int candidateMultiplier = 3;
        /** 可选最低分过滤 */
        private Double minScore;
        private String cohereApiKey = "";
        private String cohereModel = "rerank-multilingual-v3.0";
        private String jinaApiKey = "";
        private String jinaModel = "jina-reranker-v2-base-multilingual";
        private String onnxModelPath = "./data/models/bge-reranker-base.onnx";
        private String onnxTokenizerPath = "./data/models/bge-reranker-tokenizer.json";
        private int onnxMaxSeqLen = 512;
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
