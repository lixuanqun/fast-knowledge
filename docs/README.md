# Fast Knowledge 文档索引

> **Fast Knowledge（快速知识库）** — 面向中小企业的开源私有化知识库：单实例部署、LangChain4j RAG、**LLM 中立**、数据本地可控。  
> **更新日期**：2026-07-03

---

## 产品定位（摘要）

| 维度 | 定位 |
|------|------|
| **目标用户** | 中小企业、团队、部门 |
| **部署模式** | 单实例私有化（非多租户 SaaS） |
| **数据规模** | 万级文档、数百用户 |
| **隐私** | Privacy by Default |
| **AI** | LLM 中立；本地 Embedding/Rerank 可选 |

**设计原则**：Privacy by Default · Single Instance · Unified Stack · Docker First · LangChain4j Native

**完整定位与功能特性清单** → **[产品说明.md](./产品说明.md)**（含分模块 ✓ 能力表）

---

## 读者指引

| 你是谁 | 从这里开始 |
|--------|------------|
| 首次了解项目 | [../README.md](../README.md) |
| 产品 / 功能清单 | [产品说明.md](./产品说明.md) |
| 前端开发 | [api.md](./api.md) |
| 后端开发 | [backend-design.md](./backend-design.md) + [api.md](./api.md) |
| 运维 / 部署 | [deployment/docker.md](./deployment/docker.md) + [../.env.example](../.env.example) |
| AI / 模型 | [deployment/llm-providers.md](./deployment/llm-providers.md) |
| 架构 | [fast_knowledge.md](./fast_knowledge.md) · [architecture/README.md](./architecture/README.md) |
| GitHub 仓库展示 | [../.github/ABOUT.md](../.github/ABOUT.md) |

---

## 文档清单

| 文档 | 内容 |
|------|------|
| [产品说明.md](./产品说明.md) | **产品定位、功能特性清单、场景、API 概览** |
| [api.md](./api.md) | REST + SSE 接口契约 |
| [backend-design.md](./backend-design.md) | 后端分层与开发清单 |
| [fast_knowledge.md](./fast_knowledge.md) | LangChain4j 管道、配置详解 |
| [architecture/README.md](./architecture/README.md) | Monorepo 与包职责 |
| [deployment/docker.md](./deployment/docker.md) | Docker Compose 部署 |
| [deployment/llm-providers.md](./deployment/llm-providers.md) | LLM / Embedding / Rerank |

## 模块 README

| 路径 | 内容 |
|------|------|
| [../README.md](../README.md) | 项目入口（GitHub 首屏） |
| [../apps/server/README.md](../apps/server/README.md) | 服务端与 LangChain4j |
| [../web/README.md](../web/README.md) | 前端开发与页面 |
| [../data/models/README.md](../data/models/README.md) | ONNX 模型准备 |

---

## 技术架构（摘要）

```
摄入 → KbEmbeddingIngestor → PgVectorEmbeddingStore (HYBRID)
检索 → SearchService → [Rerank] → RAG 问答/对话/写文档
LLM  → LlmModelRegistry（UI 配置热刷新）
存储 → PostgreSQL · Redis · MinIO
```

**已移除**：SQLite、sqlite-vec、自研 VectorStore SPI、Caffeine、本地文件存储。

---

## 变更记录

| 日期 | 说明 |
|------|------|
| 2026-07-03 | 统一产品定位与功能清单；LLM 中立 UI 配置；GitHub 展示文档 |
| 2026-07-02 | LangChain4j 重构后文档体系 |
