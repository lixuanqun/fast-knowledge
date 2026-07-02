# Fast Knowledge — 快速知识库

[![CI](https://github.com/lixuanqun/fast-knowledge/actions/workflows/ci.yml/badge.svg)](https://github.com/lixuanqun/fast-knowledge/actions/workflows/ci.yml)
[![CD](https://github.com/lixuanqun/fast-knowledge/actions/workflows/cd.yml/badge.svg)](https://github.com/lixuanqun/fast-knowledge/actions/workflows/cd.yml)
[![License: AGPL v3](https://img.shields.io/badge/License-AGPL%20v3-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange)](apps/server/)
[![Vue](https://img.shields.io/badge/Vue-3.5-brightgreen)](web/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen)](apps/server/)

面向**中小企业**的开源私有化知识库：**单机开箱即用、集群可扩展、数据本地可控**。

> 详细产品说明见 [docs/产品说明.md](docs/产品说明.md) · 架构与配置见 [docs/fast_knowledge.md](docs/fast_knowledge.md)

---

## 产品定位

Fast Knowledge 专为「每家企业独立部署一套」的轻量知识管理场景设计，而非多租户 SaaS 平台。

| 维度 | 说明 |
|------|------|
| **目标用户** | 中小企业、团队、部门级知识管理 |
| **数据规模** | 千级文档、数十用户 |
| **隐私优先** | 文档、向量、对话默认留在本地；LLM 调用外部 API |
| **部署形态** | 单机单 Jar（Linux 开箱即用）或 Docker/K8s 集群 |
| **数据底座** | 单机 SQLite+sqlite-vec · 集群 PostgreSQL+pgvector |

---

## 核心能力

### 知识管理

- 工作区 + 知识库 CRUD，支持 **PUBLIC / PRIVATE** 可见性
- 知识库级成员 ACL（**READ / WRITE / ADMIN**）
- 多格式文档上传：**PDF、DOCX、TXT、MD、PPTX、XLSX、HTML**（Tika 解析）
- 异步索引、分块预览、单文档重建、整库重建

### 智能检索与 AI

- **混合检索**：向量 + 全文，加权融合（alpha 可配）
- **智能问答**：RAG 单轮问答，附引用来源
- **智能对话**：多轮 RAG + SSE 流式输出，会话持久化
- **智能写文档**：按主题/大纲生成 Markdown，可引用知识库并直接入库

### 安全与运维

- JWT 认证、首次安装向导（强制改密）、用户管理
- 运营概览：文档/索引统计、待处理任务、审计日志
- 启动时自动初始化表结构，无需手动执行 SQL

### 部署模式

| 模式 | 业务+向量库 | 缓存 | 文件 | 适用 |
|------|------------|------|------|------|
| **单机 standalone** | SQLite + sqlite-vec | Caffeine | 本地磁盘 | Linux 服务器开箱即用 |
| **集群 prod** | PostgreSQL + pgvector | Redis | MinIO | Docker / K8s 多副本 |

---

## 技术栈

**后端** Java 21 · Spring Boot 3.5 · LangChain4j · MyBatis Plus · Tika  
**前端** Vue 3 · Vite 6 · TypeScript · Element Plus  
**单机数据** SQLite · sqlite-vec · Caffeine  
**集群数据** PostgreSQL · pgvector · Redis · MinIO

---

## Monorepo 结构

```
fast-knowledge/
├── pom.xml                     # Maven 父工程
├── apps/server/                # Spring Boot 服务端
├── web/                        # Vue 3 前端
├── docker/                     # Dockerfile、Compose
├── data/models/                # ONNX 模型（运行时挂载）
├── docs/
└── scripts/                    # dev / build / install
```

**构建：**

```bash
mvn -pl apps/server -am clean package -DskipTests -Pbundle   # 单 Jar（含前端）
mvn -pl apps/server -am test                                  # 后端测试
```

---

## 快速启动

### 方式 A：单机（推荐试用）

```bash
mvn -pl apps/server spring-boot:run -Dspring-boot.run.profiles=standalone,bundle
```

访问 http://localhost:8088 ，默认 **admin / admin123**。

无需 MySQL、Redis 或 Docker。

### 方式 B：Docker 集群

```bash
./scripts/install.sh
# 或：cd docker && docker compose -f docker-compose.full.yml up -d --build
```

需配置 `JWT_SECRET`、`LLM_API_KEY` 等，见 [docker/.env.example](docker/.env.example)。

### 方式 C：本地开发

```powershell
scripts/dev.ps1    # standalone 后端 + Vite 前端
```

- 前端：http://localhost:5174
- API：http://localhost:8088/api

集群开发：`cd docker; docker compose up -d`，复制 `application-local.example.yml` 后以 `prod,local,bundle` 启动。

---

## 环境变量

| 变量 | 单机 | 集群 |
|------|------|------|
| `SQLITE_DB_PATH` | SQLite 文件路径 | — |
| `SQLITE_VEC_EXTENSION` | sqlite-vec 扩展路径 | — |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | — | PostgreSQL |
| `REDIS_HOST` 等 | — | Redis |
| `MINIO_ENDPOINT` / `MINIO_BUCKET` 等 | — | MinIO 对象存储 |
| `LLM_BASE_URL` / `LLM_API_KEY` | 外部 LLM API | 同左 |
| `JWT_SECRET` | 可选（有默认） | **必须** |

---

## Web 功能入口

| 页面 | 路径 |
|------|------|
| 概览 | `/dashboard` |
| 知识库 | `/kbs` |
| 智能检索 | `/search` |
| 智能问答 | `/qa` |
| 智能对话 | `/chat` |
| 智能写文档 | `/writer` |
| 用户管理 | `/users` |

---

## 许可证

**AGPL-3.0 + 商业双许可** — 详见 [LICENSE](LICENSE) 与 [LICENSE-COMMERCIAL.md](LICENSE-COMMERCIAL.md)。

**Topics**：`knowledge-base` · `rag` · `semantic-search` · `self-hosted` · `spring-boot` · `vue3` · `sqlite` · `pgvector` · `docker`

---

## CI / CD

| 工作流 | 说明 |
|--------|------|
| **CI** | `mvn test`、前端构建、Docker 镜像校验 |
| **CD** | 推送 GHCR 镜像，`v*` 标签创建 Release |

```bash
docker pull ghcr.io/lixuanqun/fast-knowledge:latest
```
