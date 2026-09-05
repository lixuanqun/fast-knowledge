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
    private Writer writer = new Writer();
    private Vision vision = new Vision();
    private ImageGen imageGen = new ImageGen();
    private Chat chat = new Chat();
    private QueryRewrite queryRewrite = new QueryRewrite();
    private Index index = new Index();
    private Agentic agentic = new Agentic();
    /**
     * 发行版：community（默认）| enterprise。
     * 可用环境变量 KNOWLEDGE_EDITION 覆盖；enterprise profile 默认 enterprise。
     */
    private String edition = "community";

    public boolean isEnterprise() {
        return "enterprise".equalsIgnoreCase(edition != null ? edition.trim() : "");
    }

    @Data
    public static class Wiki {
        private boolean enabled = true;
        /** false 时 Wiki 页保持 DRAFT，需管理员审核 */
        private boolean autoPublish = false;
        /** 章节/制度/目录类问法优先走已发布 Wiki，否则 HYBRID */
        private boolean queryRouting = true;
        /** Wiki 维护 Agent（langgraph4j 图编排：增量合并 + Lint 循环 + 变更日志），需企业版 */
        private Agent agent = new Agent();
    }

    @Data
    public static class Agent {
        /** 默认关闭；开启需叠加企业版门控（EditionGuard） */
        private boolean enabled = false;
        /** lint→revise 循环封顶轮数 */
        private int maxLintIterations = 2;
        /** LLM 语义 Lint（关闭则仅规则 Lint） */
        private boolean llmLintEnabled = true;
    }

    @Data
    public static class Vision {
        /** 视觉问答（qwen-vl 系列，云端）；内网纯离线模式不可用 */
        private boolean enabled = true;
        private String model = "qwen-vl-plus";
        /** 单图大小上限（MB） */
        private int maxImageMb = 10;
    }

    @Data
    public static class ImageGen {
        /** 文生图（wanx 系列，DashScope 原生异步任务 API） */
        private boolean enabled = true;
        private String model = "wanx2.1-t2i-turbo";
        /** 生成尺寸，如 1024*1024 */
        private String size = "1024*1024";
        /** 任务轮询超时（秒） */
        private int pollTimeoutSeconds = 120;
    }

    @Data
    public static class Writer {
        /** 写文档多步编排（大纲→分节→引用→润色），默认走单次生成 */
        private boolean graphEnabled = false;
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
        /** 本地文件向量索引（LocalEmbeddingStore：内存检索 + JSON 持久化） */
        private Local local = new Local();
    }

    @Data
    public static class Local {
        /** 本地向量索引持久化目录（per-KB JSON 文件） */
        private String storageDir = "./data/vectors";
    }

    @Data
    public static class Cache {
        private String provider = "redis";
        private L1 l1 = new L1();
    }

    @Data
    public static class L1 {
        /** 是否启用本地 Caffeine L1 缓存 */
        private boolean enabled = true;
        /** L1 缓存最大条目数 */
        private int maxSize = 500;
        /** L1 缓存 TTL（分钟） */
        private int ttlMinutes = 2;
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
        /** minio（私有化/离线）| oss（阿里云对象存储） */
        private String provider = "minio";
        private Minio minio = new Minio();
        private Oss oss = new Oss();
    }

    @Data
    public static class Oss {
        /** 如 https://oss-cn-hangzhou.aliyuncs.com */
        private String endpoint = "https://oss-cn-hangzhou.aliyuncs.com";
        private String bucket = "fast-knowledge";
        private String accessKey = "";
        private String secretKey = "";
        /** 签名区域，缺省从 endpoint 推断（oss-cn-hangzhou → cn-hangzhou） */
        private String region = "";
        /** 对象前缀，如 knowledge/ */
        private String prefix = "knowledge/";
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
        /** openai（OpenAI 兼容 /v1/embeddings，含 DashScope compatible-mode）| ollama | hash */
        private String provider = "openai";
        private String openaiBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        private String openaiApiKey = "";
        private String openaiModel = "text-embedding-v3";
        /** 向量维度（云端模型各不相同，须与 provider 输出一致） */
        private int dimension = 1024;
        private String ollamaUrl = "http://localhost:11434";
        private String ollamaModel = "nomic-embed-text";
        /** 是否缓存 query embedding 结果 */
        private boolean cacheEnabled = true;
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
        /** cohere | jina（云端） */
        private String provider = "cohere";
        /** 初召回倍数：先取 topK * multiplier，再 rerank 截断 */
        private int candidateMultiplier = 3;
        /** 可选最低分过滤 */
        private Double minScore;
        private String cohereApiKey = "";
        private String cohereModel = "rerank-multilingual-v3.0";
        private String jinaApiKey = "";
        private String jinaModel = "jina-reranker-v2-base-multilingual";
    }

    @Data
    public static class Chat {
        /** LangChain4j MessageWindowChatMemory 窗口大小（条） */
        private int memoryWindow = 10;
    }

    @Data
    public static class QueryRewrite {
        /** 是否启用查询改写 */
        private boolean enabled = true;
        /** 用于改写的对话历史轮数 */
        private int historyRounds = 5;
    }

    @Data
    public static class Agentic {
        /** 复杂问法启用有限多跳拆解检索 */
        private boolean enabled = true;
        /** 子查询上限（含原问，实际额外跳数 = maxSubQueries - 1） */
        private int maxSubQueries = 3;
        /** 是否用 LLM 拆解子查询；false 则仅启发式拆分 */
        private boolean llmDecompose = true;
    }

    @Data
    public static class Index {
        /** 是否启用 Redis Pub/Sub 事件驱动索引（关闭则回退轮询） */
        private boolean pubsubEnabled = true;
        /** 最大重试次数 */
        private int maxRetry = 3;
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
