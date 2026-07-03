# Fast Knowledge

**面向国企与制造业的 Java 私有化知识库** — 把制度、工艺、设备文档变成可检索、可问答、可审计的企业知识资产。

[![CI](https://github.com/lixuanqun/fast-knowledge/actions/workflows/ci.yml/badge.svg)](https://github.com/lixuanqun/fast-knowledge/actions/workflows/ci.yml)
[![License: AGPL v3](https://img.shields.io/badge/License-AGPL%20v3-blue.svg)](LICENSE)
[![Java 21](https://img.shields.io/badge/Java-21-orange)](apps/server/)
[![Spring Boot 3.5](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen)](apps/server/)
[![Vue 3](https://img.shields.io/badge/Vue-3.5-brightgreen)](web/)

[产品说明](docs/产品说明.md) · [v1.0.0 功能范围](docs/releases/v1.0.0.md) · [快速部署](docs/deployment/docker.md) · [API](docs/api.md)

---

## 一句话

在你自己的服务器上，部署一套**数据不出域**的知识库 + 智能检索 + RAG 问答系统。  
用 **Java / Spring Boot** 技术栈交付，用 **审计与离线包** 验收，**不做**通用 Agent 工作流平台。

---

## 你能用它做什么

| 场景 | 能做什么 |
|------|----------|
| **制度 / 红头文件** | 按文号、类型检索；问答附引用来源，一键跳转原文定位 |
| **工艺 / SOP / 设备手册** | 上传 PDF/Word，混合检索找段落，多轮对话追问细节 |
| **系统集成** | REST API + 服务账号 API Key，对接 OA、MES、门户 |

---

## 核心特性

### 知识库与 AI

- **混合检索** — 向量 + 全文（pgvector HYBRID），可选本地 ONNX Rerank
- **RAG 问答** — 单次问答、多轮流式对话、AI 写文档，均附引用来源
- **多格式文档** — PDF / DOCX / TXT / MD / PPTX / XLSX / HTML，异步索引与分块预览
- **LLM 中立** — Ollama、DeepSeek、智谱、百炼等 OpenAI 兼容接口，管理界面配置即生效
- **本地 Embedding** — 默认 ONNX `bge-small-zh-v1.5`，可纯内网运行

### 制造 / 国企场景（v1.0.0）

- **文档元数据** — 类型（制度/工艺/设备…）、文号、生效日期、部门、标签
- **引用溯源** — 检索与问答返回章节信息，点击可预览并高亮对应分块
- **Wiki 基础层** — 文档索引后自动编译 Markdown 知识页（浏览，审核流完善中）

### 企业交付与安全

- **LDAP + OIDC** — 对接企业统一身份，保留本地管理员兜底
- **全链路审计** — 登录、检索、问答、对话可查可导出 CSV
- **不出域模式** — `LLM_ALLOW_EXTERNAL=false` 禁止外连大模型与云端 Rerank
- **离线交付包** — 气隙/内网环境镜像与安装脚本
- **API Key** — 服务账号调用，适合后端系统集成
- **备份恢复** — PostgreSQL + MinIO 一键备份脚本与 Runbook

### 部署方式

```bash
./scripts/install.sh          # Linux/macOS 一键 Docker 全栈
# .\scripts\install.ps1       # Windows
```

访问 http://localhost:8088 · 默认账号 `admin` / `admin123`

也支持：单 Jar（`-Pbundle`）· K8s 清单 · [离线安装](docs/deployment/offline-install.md) · [企业配置](apps/server/src/main/resources/application-enterprise.yml)

---

## 适合谁 / 不适合谁

| 适合 | 不适合 |
|------|--------|
| 国企、金融、制造等需**私有化部署**的组织 | 多租户 SaaS 运营平台 |
| 万级文档、数百用户的部门/企业知识库 | 十万级文档、千人并发搜索中台 |
| 技术栈以 **Java** 为主、需 REST 集成的团队 | 要可视化工作流 / MCP / 多模态 Agent 平台 |
| 合同要写清「**数据不出域、行为可审计**」的项目 | 不愿自建任何 AI 组件且不接受 ONNX/Ollama |

---

## 和通用知识库产品有什么不同

| | 通用 Agent 平台 | Fast Knowledge |
|--|----------------|----------------|
| 定位 | 智能体、工作流、工具编排 | **知识库专用** |
| 技术栈 | 多为 Python | **Java 21 + Spring Boot** |
| 集成方式 | iframe / SDK 为主 | **REST + API Key** |
| 合规叙事 | 通用私有化 | **审计导出 + 不出域验收清单 + 离线包** |
| 垂直场景 | 通用 | **文号、文档类型、引用溯源** |

---

## 界面一览

登录后可使用：知识库管理 · 智能检索 · 智能问答 · 多轮对话 · AI 写文档

管理员额外拥有：用户管理 · 审计日志 · API Key · 大模型配置

```
概览 → 知识库 → 上传文档 → 检索 / 问答 / 对话
                              ↓
                        引用来源 → 跳转原文高亮
```

---

## 技术架构（简图）

```
Vue 3 管理端
    ↓
Spring Boot API（JWT / LDAP / OIDC / API Key）
    ↓
PostgreSQL + pgvector · Redis · MinIO
    ↓
LangChain4j — 摄入 / HYBRID 检索 / RAG / 对话 / Rerank
    ↓
LLM（OpenAI 兼容，可纯内网 Ollama）
```

**技术栈**：Java 21 · Spring Boot 3.5 · LangChain4j · Vue 3 · PostgreSQL · Redis · MinIO

本地开发：`.\scripts\dev.ps1`（后端 `:8088/api`，前端 `:5174`）

---

## 文档

| 我想… | 去看 |
|-------|------|
| 了解完整功能清单 | [产品说明](docs/产品说明.md) |
| 看 v1.0.0 已交付什么 | [docs/releases/v1.0.0.md](docs/releases/v1.0.0.md) |
| 部署与运维 | [Docker](docs/deployment/docker.md) · [备份恢复](docs/deployment/backup-restore.md) · [K8s](k8s/README.md) |
| 对接 API | [docs/api.md](docs/api.md) |
| 数据不出域验收 | [合规清单](docs/compliance/data-residency-checklist.md) |

---

## 仓库结构

```
apps/server/   后端（Spring Boot + LangChain4j）
web/           前端（Vue 3）
docker/        Docker Compose
scripts/       安装、开发、备份、离线交付
docs/          产品、架构、部署、合规
```

---

## 许可证

**AGPL-3.0 + 商业双许可** — 社区版开源使用见 [LICENSE](LICENSE)；企业商业授权见 [LICENSE-COMMERCIAL.md](LICENSE-COMMERCIAL.md)。

---

<p align="center">
  <sub>当前开发分支 <code>develop_1.0.0</code> · 规划中的 v2.0.0 能力见 <a href="docs/releases/v2.0.0.md">发布说明</a></sub>
</p>
