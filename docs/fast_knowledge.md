# Fast Knowledge — 技术架构与配置

> 产品定位与功能特性见 [产品说明.md](./产品说明.md)。

## 架构概览

```
Vue3 前端 ──► Spring Boot API
                    │
        ┌───────────┴───────────┐
        ▼                       ▼
  单机 standalone          集群 prod
  SQLite + sqlite-vec      PostgreSQL + pgvector
  Caffeine + 本地文件       Redis + MinIO
        │                       │
        └──────── LLM 外部 API ─┘
```

## 设计原则

1. **Privacy by Default**：文档、向量、对话默认不出服务器（LLM 走外部 API）
2. **Single Instance First**：单机 Linux 开箱即用，零中间件
3. **Unified Data Base**：业务表与向量表在同一数据库引擎内
4. **Progressive Complexity**：单机 SQLite → 集群 PostgreSQL + MinIO

## 数据模型

| 表 | 说明 |
|----|------|
| kb_user | 用户（ADMIN / USER） |
| kb_workspace | 工作区 |
| kb_knowledge_base | 知识库 |
| kb_kb_member | KB 级 ACL |
| kb_document / kb_document_chunk | 文档与分块 |
| kb_index_task | 异步索引任务 |
| kb_vector_chunk | 向量索引 |
| kb_chat_session / kb_chat_message | 对话 |
| kb_audit_log | 审计日志 |
| kb_system_config | 实例配置 |

Schema：

- 单机：`db/schema-sqlite.sql`
- 集群：`db/schema-postgres.sql`

## 向量存储

```yaml
knowledge:
  vector:
    provider: sqlite-vec   # standalone
    # provider: pgvector   # prod
```

| 实现 | 模式 | 混合检索 |
|------|------|----------|
| sqlite-vec | standalone | FTS5 + 向量 |
| pgvector | prod | tsvector + pgvector |

## 缓存与存储

单机：`caffeine` + `local`  
集群：`redis` + `minio`

## Embedding

- 生产：`onnx` + BGE 模型
- 演示：`hash`（`minimal` profile）
- 可选：`ollama`

## LangChain4j

| 组件 | 实现 |
|------|------|
| LLM | OpenAI 兼容 API |
| 向量 | `KbEmbeddingStore` → `VectorStore` SPI |
| RAG | `KbHybridContentRetriever` → `SearchService` |

## 部署

| 模式 | Profile |
|------|---------|
| 单机 | `standalone,bundle` |
| 集群 | `prod,bundle` |

详见 [deployment/docker.md](./deployment/docker.md)。
