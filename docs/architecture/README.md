# Fast Knowledge 模块结构

面向中小企业的开源私有化知识库 Monorepo，采用 **前后端分离 + Java 单体后端** 架构。

## 仓库布局

```
fast-knowledge/
├── backend/                 # Java 21 后端（核心业务）
├── frontend/                # Vue 3 前端（管理界面）
├── deploy/                  # Docker / Nginx / 环境变量模板
├── docs/                    # 产品与技术文档
│   ├── architecture/        # 架构与模块说明（本目录）
│   └── deployment/          # 部署指南
├── models/                  # ONNX 模型与 tokenizer（不入库）
├── scripts/                 # 构建与安装脚本
├── package.json             # 根 workspace（frontend）
├── LICENSE
└── LICENSE-COMMERCIAL.md
```

## 后端分层（`com.fast.knowledge`）

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

## 前端模块

| 目录 | 职责 |
|------|------|
| `src/api/` | 按域拆分 API（auth / kb / search / config） |
| `src/stores/` | Pinia 状态（auth / kb / config） |
| `src/views/` | 页面（login / setup / kbs / qa / chat） |
| `src/components/` | 可复用组件 |
