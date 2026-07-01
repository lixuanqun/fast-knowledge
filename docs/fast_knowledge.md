# Fast Knowledge — 技术架构与配置

> 产品定位与功能特性见 [产品说明.md](./产品说明.md)。

## 架构概览

```
Vue3 前端 ──► Spring Boot API ──► MySQL（业务数据）
                    │
                    ├── VectorStore SPI ──► Lucene（默认）/ pgvector / Qdrant
                    ├── EmbeddingProvider ──► ONNX / Ollama / hash
                    ├── CacheProvider ──► Caffeine（默认）/ Redis
                    └── LangChain4j RAG ──► 本地或兼容 OpenAI 的 LLM
```

## 设计原则

1. **Privacy by Default**：文档、向量、对话默认不出服务器
2. **Single Instance**：每企独立部署，轻量工作区组织知识库
3. **Pluggable Backends**：向量库、缓存一行 YAML 切换
4. **Progressive Complexity**：开发 hash → Docker 演示 → 生产 ONNX/Ollama

## 数据模型

| 表 | 说明 |
|----|------|
| kb_user | 用户（ADMIN / USER），支持首次改密 |
| kb_workspace | 工作区 |
| kb_knowledge_base | 知识库（workspace_id、PUBLIC/PRIVATE、search_alpha/top_k） |
| kb_kb_member | KB 级 ACL：READ / WRITE / ADMIN |
| kb_system_config | 实例配置、安装向导状态 |
| kb_document / kb_document_chunk | 文档与分块 |
| kb_index_task | 异步索引任务（含 DB 锁 + 缓存锁） |
| kb_chat_session / kb_chat_message | 多轮对话会话与消息（含 sources JSON） |
| kb_audit_log | 操作审计日志 |

Schema 由 Flyway 管理：`apps/server/src/main/resources/db/migration/`。

## 向量存储配置

```yaml
knowledge:
  vector:
    provider: lucene   # lucene | pgvector | qdrant
```

- **lucene**：内置，零外部依赖，HNSW + SmartChinese BM25 混合检索
- **pgvector**：需 PostgreSQL + pgvector 扩展及 `kb_vector_chunk` 表
- **qdrant**：`docker compose --profile qdrant` 启动 Qdrant 容器

## 缓存配置

```yaml
knowledge:
  cache:
    provider: caffeine   # caffeine | redis
```

- **caffeine**：进程内缓存，开发与小规模部署无需 Redis
- **redis**：生产推荐，用于检索缓存、Token 黑名单、索引分布式锁

## Embedding 建议

1. **生产离线**：`EMBEDDING_PROVIDER=onnx` + `data/models/bge-small-zh-v1.5.onnx` + `tokenizer.json`
2. **Docker 演示**：`EMBEDDING_PROVIDER=ollama` + `nomic-embed-text`
3. **开发联调**：`EMBEDDING_PROVIDER=hash`（无需外部服务，质量有限）

## 分块与检索

```yaml
knowledge:
  chunk:
    size: 512
    overlap: 50
  search:
    default-top-k: 8
    hybrid-alpha: 0.6
```

分块策略：段落感知拆分 → Markdown 标题识别 → jtokkit token 计数 → 滑动窗口兜底。

## LangChain4j 集成

| 组件 | 实现 |
|------|------|
| LLM | `OpenAiChatModel` / `OpenAiStreamingChatModel` |
| 向量存储 | `LuceneEmbeddingStore`（LangChain4j EmbeddingStore SPI） |
| RAG 检索 | `KbHybridContentRetriever`（HNSW + BM25 混合） |
| Embedding | `ProviderEmbeddingModel` 桥接 hash / Ollama / ONNX |

## 部署模式

| 模式 | 命令 | 说明 |
|------|------|------|
| 全栈 Docker | `scripts/install.sh` | 应用 + MySQL + Redis |
| 单 Jar | `npm run build:jar` | 前后端同端口 8088（profile=bundle） |
| Nginx 分离 | `npm run build` | 静态 dist + API 反代 |

## API

启动后访问 `/api/swagger-ui.html`。

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 登录 |
| POST | `/api/auth/setup` | 首次安装向导 |
| GET | `/api/system/config` | 公开系统配置 |
| GET | `/api/kbs` | 知识库列表（含 PUBLIC） |
| POST | `/api/kbs/{id}/documents/upload` | 上传文档 |
| POST | `/api/search` | 混合检索 |
| POST | `/api/qa` | RAG 问答 |
| POST | `/api/chat/messages/stream` | 多轮对话（SSE） |
| POST | `/api/writer/generate` | 智能写文档（SSE） |
| GET | `/api/dashboard/stats` | 运营概览 |
| GET | `/api/index-tasks/*` | 索引任务管理 |

## 本地开发

```powershell
npm install
scripts/dev.ps1
```

默认 `CACHE_PROVIDER=caffeine`，无需 Redis 即可启动后端（LLM 仍需配置才有问答能力）。
