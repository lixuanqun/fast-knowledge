# Fast Knowledge — 快速知识库

[![CI](https://github.com/lixuanqun/fast-knowledge/actions/workflows/ci.yml/badge.svg)](https://github.com/lixuanqun/fast-knowledge/actions/workflows/ci.yml)
[![CD](https://github.com/lixuanqun/fast-knowledge/actions/workflows/cd.yml/badge.svg)](https://github.com/lixuanqun/fast-knowledge/actions/workflows/cd.yml)
[![License: AGPL v3](https://img.shields.io/badge/License-AGPL%20v3-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange)](apps/server/)
[![Vue](https://img.shields.io/badge/Vue-3.5-brightgreen)](web/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen)](apps/server/)
[![LangChain4j](https://img.shields.io/badge/LangChain4j-1.17-blueviolet)](apps/server/)

**面向中小企业的开源私有化知识库** — 单实例部署、数据本地可控、**LLM 中立**、Docker 一键启动。

[产品说明](docs/产品说明.md) · [功能特性清单](docs/产品说明.md#二功能特性清单) · [v1.0.0 版本范围](docs/releases/v1.0.0.md) · [技术架构](docs/fast_knowledge.md) · [API 文档](docs/api.md)

---

## 这是什么？

Fast Knowledge 帮助中小企业在**自己的服务器**上搭建一套知识库 + RAG 问答系统：

- **不是**多租户 SaaS — 每家企业独立部署一套（Single Instance）
- **默认**文档、向量、对话留在本地（Privacy by Default）
- **兼容**任意 OpenAI 协议大模型 — 管理员可在 UI 切换提供商，无需改代码
- **基于** LangChain4j + PostgreSQL/pgvector 混合检索，Docker Compose 即可拉起全栈

---

## 特性一览

| 类别 | 能力 |
|------|------|
| **部署** | Docker / 单 Jar / K8s · `install.sh` & `install.ps1` 一键全栈 · GHCR 镜像 |
| **知识库** | 工作区 · PUBLIC/PRIVATE · 成员 ACL（READ/WRITE/ADMIN） |
| **文档** | PDF/DOCX/TXT/MD/PPTX/XLSX/HTML · 异步索引 · 分块预览 · 重建索引 |
| **检索** | PgVector **HYBRID**（向量+全文 RRF）· 可选 ONNX/Cohere/Jina Rerank |
| **AI** | 智能检索 · RAG 问答 · 多轮流式对话 · AI 写文档（均附来源引用） |
| **LLM** | **中立配置**：Ollama / DeepSeek / 智谱 / 百炼 / 火山 / OpenAI / 自定义 · UI 保存热生效 |
| **安全** | JWT · 首次安装向导 · 用户管理 · 审计日志 · 可禁止外连 LLM |
| **Embedding** | 默认 ONNX 本地 `bge-small-zh-v1.5`（可换 Ollama / hash 演示） |

完整功能表见 [docs/产品说明.md#二功能特性清单](docs/产品说明.md#二功能特性清单)。

---

## 产品定位

| 维度 | 说明 |
|------|------|
| 目标用户 | 中小企业、团队、部门 |
| 部署模式 | 单实例私有化，非多租户 SaaS |
| 数据规模 | 万级文档、数百用户 |
| 隐私 | Privacy by Default |
| AI | LLM 中立 + 本地 Embedding/Rerank 可选 |

**设计原则**：Privacy by Default · Single Instance · Unified Stack · Docker First · LangChain4j Native

**适用**：制度手册检索、FAQ 问答、研发文档沉淀、内网合规部署。  
**不适用**：十万级文档/千人并发搜索平台、多租户 SaaS 运营。

---

## 架构概览

```
Vue 3 ──► Spring Boot API (JWT)
              │
   PostgreSQL + pgvector (HYBRID) · Redis · MinIO
              │
        LangChain4j — 摄入 / 检索 / RAG / 对话 / Rerank
              │
        LLM (OpenAI 兼容) ← 管理员 UI 配置，热刷新
```

| 链路 | 组件 |
|------|------|
| 摄入 | `KbDocumentSplitter` → `KbEmbeddingIngestor` → `PgVectorEmbeddingStore` |
| 检索 | `SearchService` → HYBRID → 可选 `SearchRerankService` |
| 问答/对话 | `RagService` / `KbChatAssistant` + `DbChatMemoryStore` |
| LLM | `LlmModelRegistry` — UI 保存后立即生效 |

---

## 快速启动

### Docker 全栈（推荐体验）

```bash
./scripts/install.sh          # Linux/macOS
# .\scripts\install.ps1       # Windows
```

访问 http://localhost:8088 · 账号 `admin` / `admin123`

### 本地开发

```powershell
.\scripts\dev.ps1
# 后端 http://localhost:8088/api · 前端 http://localhost:5174
```

### 更多

| 场景 | 说明 |
|------|------|
| [Docker 部署](docs/deployment/docker.md) | 环境变量、备份、FAQ |
| [K8s](k8s/README.md) | Secret、依赖、Ingress |
| [LLM 配置](docs/deployment/llm-providers.md) | 预设提供商与管理界面 |
| [ONNX 模型](data/models/README.md) | Embedding / Reranker 文件 |
| 演示模式 | `-Dspring-boot.run.profiles=minimal,bundle`（免 ONNX） |
| 预构建镜像 | `docker pull ghcr.io/lixuanqun/fast-knowledge:latest` |

```bash
mvn -pl apps/server -am clean package -DskipTests -Pbundle   # 构建单 Jar
```

---

## 技术栈

Java 21 · Spring Boot 3.5 · LangChain4j 1.17 · Vue 3 · Vite 6 · PostgreSQL + pgvector · Redis · MinIO

---

## 仓库结构

```
apps/server/   Spring Boot + LangChain4j
web/           Vue 3 管理界面
docker/        Dockerfile & Compose
k8s/           Kubernetes 清单
docs/          产品说明、API、架构、部署
scripts/       dev / build / install
```

---

## Web 功能入口

| 页面 | 路径 | 权限 |
|------|------|------|
| 概览 / 知识库 / 检索 / 问答 / 对话 / 写文档 | `/dashboard` … `/writer` | 登录 |
| 设置与隐私 | `/settings` | 登录 |
| **大模型配置** | `/settings/llm` | 管理员 |
| 用户管理 | `/users` | 管理员 |

---

## 文档导航

| 读者 | 文档 |
|------|------|
| 产品 / 功能清单 | [docs/产品说明.md](docs/产品说明.md) |
| 开发者 / API | [docs/api.md](docs/api.md) |
| 架构 | [docs/fast_knowledge.md](docs/fast_knowledge.md) |
| 运维 | [docs/deployment/docker.md](docs/deployment/docker.md) |
| GitHub 展示 | [.github/ABOUT.md](.github/ABOUT.md) |

---

## 许可证

**AGPL-3.0 + 商业双许可** — [LICENSE](LICENSE) · [LICENSE-COMMERCIAL.md](LICENSE-COMMERCIAL.md)

---

## CI / CD

CI：单元测试 + 前端构建 + Docker 构建校验 · CD：推送 `ghcr.io` 镜像

维护者可运行 `.\scripts\set-github-metadata.ps1` 同步 GitHub 仓库描述与 Topics。
