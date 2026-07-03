# Fast Knowledge — 技术架构与配置

> **产品定位**：[产品说明.md](./产品说明.md) — 中小企业私有化知识库；单实例、LLM 中立、Privacy by Default。  
> 文档索引：[README.md](./README.md)

## 产品定位与技术原则

| 维度 | 说明 |
|------|------|
| 目标用户 | 中小企业、团队、部门级知识管理 |
| 部署模式 | 单实例私有化（Single Instance） |
| 数据规模 | 万级文档、数百用户 |
| 隐私 | Privacy by Default：PG + MinIO 存文档与向量；Embedding/Rerank 可 ONNX 本地；`LLM_ALLOW_EXTERNAL=false` 禁止外连 LLM |

与产品定位对齐的**技术设计原则**：

1. **Privacy by Default** — 文档、向量、对话默认留在服务器；Embedding/Rerank 可 ONNX 本地
2. **Single Instance** — 单租户，轻量工作区 + 知识库 ACL，无多租户运维
3. **Unified Stack** — PostgreSQL 承载业务表与 LangChain4j 向量表（`kb_embeddings`）
4. **Docker First** — 本地 `docker compose`，生产 K8s，无 SQLite 双轨
5. **LangChain4j Native** — 摄入、检索、RAG、流式对话均走官方组件
6. **LLM Neutral** — OpenAI 兼容；`kb_system_config` + `LlmModelRegistry` 支持 UI 热更新

## 架构概览

```
Vue3 前端 ──► Spring Boot API（JWT）
                    │
        PostgreSQL + pgvector (HYBRID, kb_embeddings)
        Redis + MinIO
                    │
              LangChain4j 1.17
         摄入 / 检索 / RAG / 对话 / Rerank
                    │
              LLM API（Ollama / OpenAI 兼容）
```

本地开发与生产使用**同一套镜像与环境变量**：`docker compose` 拉起依赖，应用通过 `application.yml` + 环境变量配置。

## 数据模型

| 表 | 说明 |
|----|------|
| kb_user | 用户（ADMIN / USER） |
| kb_workspace | 工作区 |
| kb_knowledge_base | 知识库（含 `search_top_k`；`search_alpha` 已废弃） |
| kb_kb_member | KB 级 ACL |
| kb_document / kb_document_chunk | 文档与分块 |
| kb_index_task | 异步索引任务 |
| kb_chat_session / kb_chat_message | 对话与引用来源 |
| kb_audit_log | 审计日志 |
| kb_system_config | 实例与 LLM 配置（`llm.provider` 等） |

Schema：`apps/server/src/main/resources/db/schema-postgres.sql`

向量表由 **LangChain4j `PgVectorEmbeddingStore`** 自动创建（默认 `kb_embeddings`）。

> **Embedding ID**：PgVector 要求向量主键为 UUID。摄入时 `KbEmbeddingIngestor` 与 `KbEmbeddingStore` 均使用 `UUID.randomUUID()` 生成 ID；业务 chunk 关联写在 metadata 中。

## 向量与检索

```yaml
knowledge:
  vector:
    provider: pgvector
    pgvector:
      table: kb_embeddings
      search-mode: HYBRID   # 向量 + 全文，RRF 融合
      rrf-k: 60
  search:
    default-top-k: 8
    rerank:
      enabled: false
      provider: onnx        # onnx | cohere | jina
      candidate-multiplier: 3
```

| 路径 | 组件 |
|------|------|
| 摄入 | `KbDocumentSplitter` → `KbEmbeddingIngestor` → `PgVectorEmbeddingStore` |
| 检索 API | `SearchService` → `KbEmbeddingStore.search()` → `SearchRerankService` |
| 单轮 RAG | `RagService.ask` → 单次 `SearchService.search` + `ChatModel` |
| 多轮对话 | `KbChatAssistant` + `CompressingQueryTransformer` + `DbChatMemoryStore` |

> **注意**：`searchAlpha` / 请求参数 `alpha` 在 API 中保留兼容，但 HYBRID RRF **当前不使用**该字段。

## 缓存与存储

- 缓存：`redis`（检索结果、Token 黑名单、索引锁）
- 对象存储：`minio`（S3 兼容；`MINIO_REGION` 默认 `us-east-1`，见 `.env.example`）

## Embedding

| provider | 场景 |
|----------|------|
| `onnx` | 生产离线，`bge-small-zh-v1.5` |
| `ollama` | 演示 / 全本地栈 |
| `hash` | 开发联调（`minimal` profile） |

## LangChain4j 组件

| 组件 | 实现 |
|------|------|
| LLM | `ChatModel` / `StreamingChatModel`（OpenAI 兼容） |
| 向量库 | `PgVectorEmbeddingStore`（HYBRID） |
| RAG | `DefaultRetrievalAugmentor` + `KbHybridContentRetriever` |
| 对话记忆 | `MessageWindowChatMemory` + `DbChatMemoryStore` |
| Rerank | `ScoringModel`：ONNX / Cohere / Jina |

## 部署

| 场景 | 命令 |
|------|------|
| 本地开发 | `scripts/dev.ps1` 或 `docker compose up -d` + `mvn ... -Dspring-boot.run.profiles=bundle` |
| 演示（无 ONNX） | `-Dspring-boot.run.profiles=minimal,bundle` |
| 单 Jar + 静态前端 | profile `bundle` |
| K8s | `k8s/deployment.yaml` |

详见 [deployment/docker.md](./deployment/docker.md)、[deployment/llm-providers.md](./deployment/llm-providers.md)。

## 环境变量

完整列表见根目录 [.env.example](../.env.example)。

## 从旧版迁移

自 SQLite / sqlite-vec / 自研 `VectorStore` SPI 升级后：

1. 使用 PostgreSQL + Docker Compose
2. 配置 `.env`
3. 对已有知识库执行**全量 re-index**

---

## 变更记录

| 日期 | 说明 |
|------|------|
| 2026-07-03 | 产品定位同步全文档；LangChain4j 统一架构、Reranker、废弃 alpha；PG 字段类型、Embedding UUID、MinIO region |
| 2026-07-02 | 初版 |
