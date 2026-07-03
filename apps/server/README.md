# Fast Knowledge — Server 模块

Java 21 + Spring Boot 3.5 知识库 API 服务，RAG 基于 **LangChain4j 1.17**。

> **产品定位**：中小企业私有化知识库后端 — 单实例、LLM 中立、Privacy by Default。  
> [产品说明](../../docs/产品说明.md) · [功能清单](../../docs/产品说明.md#二功能特性清单) · [根 README](../../README.md)

## 核心能力

- JWT 认证 + 知识库 ACL + 用户管理
- 文档上传与异步索引（Tika）
- PgVector **HYBRID** 混合检索 + 可选 **Reranker**（ONNX / Cohere / Jina）
- LangChain4j RAG：问答 / 多轮流式对话 / 智能写文档
- **LLM 中立**：`LlmSettingsService` + `LlmModelRegistry` 管理界面配置热刷新

## 运行时依赖

| 组件 | 技术 |
|------|------|
| 数据库 + 向量 | PostgreSQL 16 + pgvector（`kb_embeddings`） |
| 缓存 | Redis |
| 文件存储 | MinIO |
| LLM | OpenAI 兼容 API / Ollama（UI 或环境变量配置） |
| Embedding | ONNX 本地（默认）/ Ollama / hash |

## 快速启动

```powershell
cd docker; docker compose up -d
mvn -pl apps/server spring-boot:run -Dspring-boot.run.profiles=bundle
```

- API：http://localhost:8088/api · Swagger：http://localhost:8088/api/swagger-ui.html
- 默认账号：**admin / admin123**
- 或使用 `scripts/dev.ps1` / `scripts/install.sh`

## LangChain4j 模块

| 包 / 类 | 说明 |
|---------|------|
| `llm/LlmConfigResolver` | LLM 预设解析（DB > env > 默认） |
| `llm/LlmModelRegistry` | ChatModel 热刷新 |
| `langchain4j/store/` | PgVector HYBRID |
| `langchain4j/ingest/` | 分块与向量化 |
| `langchain4j/rerank/` | 可选检索重排序 |
| `service/RagService` | 单轮 RAG 问答 |
| `langchain4j/assistant/KbChatAssistant` | 流式多轮对话 |
| `langchain4j/memory/DbChatMemoryStore` | 会话记忆 |

## 配置 Profile

| Profile | 用途 |
|---------|------|
| `bundle` | 单 Jar 托管前端 |
| `minimal` | hash 伪向量，免 ONNX |

## 测试

```bash
mvn -pl apps/server test -Dtest="!*Integration*"
```

## 相关文档

- [docs/fast_knowledge.md](../../docs/fast_knowledge.md)
- [docs/api.md](../../docs/api.md)
- [docs/deployment/llm-providers.md](../../docs/deployment/llm-providers.md)
