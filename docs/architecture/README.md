# Fast Knowledge 模块结构

面向中小企业的开源私有化知识库 Monorepo，采用 **Maven 父工程 + Spring Boot 服务端 + Vue Web 前端** 架构。

## 仓库布局

```
fast-knowledge/
├── pom.xml                  # Maven 父 POM
├── apps/
│   └── server/              # Spring Boot 应用（com.fast.knowledge）
├── web/                     # Vue 3 管理界面
├── docker/                  # 容器与 Compose
├── data/models/             # ONNX 模型（gitignore，运行时挂载）
├── docs/
├── scripts/
├── LICENSE
└── LICENSE-COMMERCIAL.md
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
| `service` | 业务编排 | `DocumentIngestService`, `RagService` |
| `security` | JWT、Spring Security | `JwtAuthenticationFilter` |
| `config` | 配置与启动 | `KnowledgeProperties`, `DataInitializer` |
| `model` | DTO / Entity / VO | `KbUser`, `SearchRequest` |
| `mapper` | MyBatis 数据访问 | `UserMapper` |
| `vector` | 向量存储 SPI | `VectorStore`, `LuceneVectorStore` |
| `embedding` | 向量化 SPI | `OnnxEmbeddingProvider`, `OllamaEmbeddingProvider` |
| `cache` | 缓存 SPI | `CaffeineCacheProvider`, `RedisCacheProvider` |
| `llm` | 大模型提供商预设与解析 | `LlmProvider`, `LlmConfigResolver` |
| `langchain4j` | LangChain4j 适配 | `LangChain4jConfig`, `LuceneEmbeddingStore` |
| `common` | 通用工具与异常 | `ApiResponse`, `BusinessException` |

## 数据流

```
上传文档 → DocumentService → DocumentIngestService
         → ChunkService（分块）→ EmbeddingProvider（向量化）
         → VectorStore（索引）→ SearchService / RagService（检索问答）
         → LlmConfigResolver → ChatModel（生成）
```

## 可插拔扩展点

| SPI | 配置项 | 默认 |
|-----|--------|------|
| VectorStore | `knowledge.vector.provider` | `lucene` |
| CacheProvider | `knowledge.cache.provider` | `caffeine` |
| EmbeddingProvider | `knowledge.embedding.provider` | `onnx` |
| LLM | `knowledge.llm.provider` | `ollama` |

## Web 模块

| 目录 | 职责 |
|------|------|
| `src/api/` | 按域拆分 API（auth / kb / search / config） |
| `src/stores/` | Pinia 状态（auth / kb / config） |
| `src/views/` | 页面（login / setup / kbs / qa / chat） |
| `src/components/` | 可复用组件 |
