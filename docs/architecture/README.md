# Fast Knowledge 模块结构

> **产品定位**：中小企业私有化知识库 — 单实例、**LLM 中立**、Privacy by Default。功能清单见 [产品说明.md](../产品说明.md)。

Monorepo 采用 **Maven 父工程 + Spring Boot 服务端 + Vue Web 前端** 架构，与「Unified Stack + Docker First」设计原则一致。

## 仓库布局

```
fast-knowledge/
├── pom.xml                  # Maven 父 POM
├── apps/
│   └── server/              # Spring Boot 应用（com.fast.knowledge）
├── web/                     # Vue 3 管理界面
├── docker/                  # 容器与 Compose
├── k8s/                     # Kubernetes 部署清单
├── data/models/             # ONNX 模型（gitignore，运行时挂载）
├── docs/
├── scripts/
├── .env.example             # 环境变量契约
├── LICENSE
```

## 构建关系

```
web (npm build) ──dist──► apps/server/target/classes/static ──► fast-knowledge-server.jar
```

由根目录 `mvn -pl apps/server -am package -Pbundle` 触发 `frontend-maven-plugin` 完成。

## 服务端分层（`com.fast.knowledge`）

| 包 | 职责 | 示例 |
|----|------|------|
| `controller` | REST / SSE API 入口 | `AuthController`, `KnowledgeBaseController` |
| `service` | 业务编排 | `DocumentIngestService`, `RagService`, `ChatService` |
| `security` | JWT、Spring Security | `JwtAuthenticationFilter` |
| `config` | 配置与启动 | `KnowledgeProperties`, `DataInitializer` |
| `model` | DTO / Entity / VO | `KbUser`, `SearchRequest` |
| `mapper` | MyBatis Plus 数据访问 | `UserMapper extends BaseMapper` |
| `embedding` | 向量化 SPI | `OnnxEmbeddingProvider`, `OllamaEmbeddingProvider` |
| `cache` | 缓存 SPI | `RedisCacheProvider` |
| `storage` | 对象存储 | `MinioStorageProvider` |
| `llm` | LLM 中立配置与热刷新 | `LlmProvider`, `LlmConfigResolver`, `LlmModelRegistry`, `LlmSettingsService` |
| `langchain4j` | LangChain4j 适配 | `LangChain4jConfig`, `KbEmbeddingStore`, `KbRetrievalAugmentorFactory` |
| `langchain4j.rerank` | 检索重排序 | `SearchRerankService`, `OnnxRerankScoringModel`, `ScoringModelConfig` |
| `common` | 通用工具与异常 | `ApiResponse`, `BusinessException` |

## 数据流

```
上传文档 → DocumentIngestService
         → KbDocumentSplitter（分块）→ KbEmbeddingIngestor（向量化）
         → PgVectorEmbeddingStore（HYBRID 索引）
         → SearchService / KbHybridContentRetriever（检索 ± Rerank）
         → RagService（单轮 QA）/ KbChatAssistant（流式对话）
         → ChatModel / StreamingChatModel（生成）
```

## 可配置扩展点

| 组件 | 配置项 | 默认 |
|-----|--------|------|
| 向量库 | `knowledge.vector.pgvector.*` | PgVector HYBRID |
| 缓存 | `knowledge.cache.provider` | `redis` |
| 对象存储 | `knowledge.storage.provider` | `minio` |
| Embedding | `knowledge.embedding.provider` | `onnx` |
| Reranker | `knowledge.search.rerank.*` | 关闭 |
| LLM | `knowledge.llm.provider` | `ollama` |

## Web 模块

| 目录 | 职责 |
|------|------|
| `src/api/` | 按域拆分 API（auth / kb / search / config） |
| `src/stores/` | Pinia 状态（auth / kb / config） |
| `src/views/` | 页面（login / setup / kbs / qa / chat） |
| `src/components/` | 可复用组件 |
