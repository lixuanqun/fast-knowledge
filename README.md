# Fast Knowledge — 快速知识库

[![CI](https://github.com/lixuanqun/fast-knowledge/actions/workflows/ci.yml/badge.svg)](https://github.com/lixuanqun/fast-knowledge/actions/workflows/ci.yml)
[![CD](https://github.com/lixuanqun/fast-knowledge/actions/workflows/cd.yml/badge.svg)](https://github.com/lixuanqun/fast-knowledge/actions/workflows/cd.yml)
[![License: AGPL v3](https://img.shields.io/badge/License-AGPL%20v3-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange)](apps/server/)
[![Vue](https://img.shields.io/badge/Vue-3.5-brightgreen)](web/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen)](apps/server/)

面向**中小企业**的开源私有化知识库：**单实例部署、数据本地不出网、开箱即用**。

> 详细产品说明见 [docs/产品说明.md](docs/产品说明.md) · 架构与配置见 [docs/fast_knowledge.md](docs/fast_knowledge.md)

---

## 产品定位

Fast Knowledge 专为「每家企业独立部署一套」的轻量知识管理场景设计，而非多租户 SaaS 平台。

| 维度 | 说明 |
|------|------|
| **目标用户** | 中小企业、团队、部门级知识管理 |
| **数据规模** | 千级文档、数十用户 |
| **隐私优先** | 文档、向量、对话默认留在本地；支持纯内网 LLM（`LLM_ALLOW_EXTERNAL=false`） |
| **部署形态** | 单实例私有化，Docker 一键启动或单 Jar 交付 |
| **扩展策略** | 向量库 / 缓存 / Embedding 可插拔，按需从 Lucene 升级到 pgvector / Qdrant |

---

## 核心能力

### 知识管理

- 工作区 + 知识库 CRUD，支持 **PUBLIC / PRIVATE** 可见性
- 知识库级成员 ACL（**READ / WRITE / ADMIN**）
- 多格式文档上传：**PDF、DOCX、TXT、MD、PPTX、XLSX、HTML**（Tika 解析）
- 异步索引、分块预览、单文档重建、整库重建

### 智能检索与 AI

- **混合检索**：Lucene HNSW 向量 + SmartChinese BM25 全文，加权融合（alpha 可配）
- **智能问答**：RAG 单轮问答，附引用来源
- **智能对话**：多轮 RAG + SSE 流式输出，会话持久化
- **智能写文档**：按主题/大纲生成 Markdown，可引用知识库并直接入库

### 安全与运维

- JWT 认证、首次安装向导（强制改密）、用户管理
- 运营概览：文档/索引统计、待处理任务、审计日志
- Flyway 自动迁移，无需手动执行 SQL

### 可插拔后端

| 组件 | 默认 | 可选 |
|------|------|------|
| 向量存储 | **Lucene**（零外部依赖） | pgvector、Qdrant |
| Embedding | **ONNX**（bge-small-zh-v1.5） | Ollama、hash（开发） |
| 缓存 | **Caffeine**（零 Redis） | Redis |
| LLM | Ollama（OpenAI 兼容 API） | 任意兼容端点 |

---

## 技术栈

**后端** Java 21 · Spring Boot 3.5 · LangChain4j · MyBatis · Flyway · Apache Lucene · Tika  
**前端** Vue 3 · Vite 6 · TypeScript · Element Plus  
**数据** MySQL 8 · Redis 7（可选）

---

## Monorepo 结构

```
fast-knowledge/
├── pom.xml                     # Maven 父工程（统一版本、构建入口）
├── apps/
│   └── server/                 # Spring Boot 服务端（Java 21）
├── web/                        # Vue 3 + Vite 前端
├── docker/                     # Dockerfile、Compose、环境变量模板
├── data/
│   └── models/                 # ONNX 模型（不入库，运行时挂载）
├── docs/
├── scripts/                    # dev / build / install 薄封装
├── .github/workflows/
├── LICENSE
└── LICENSE-COMMERCIAL.md
```

**构建入口（推荐）：**

```bash
mvn -pl apps/server -am clean package -DskipTests -Pbundle   # 单 Jar（含 web 静态资源）
mvn -pl apps/server -am test                                  # 仅后端测试
```

---

## 快速启动

### 方式 A：Docker 全栈（推荐）

```bash
# Linux / macOS
./scripts/install.sh

# 或手动
cd docker
docker compose -f docker-compose.full.yml up -d --build
```

访问 http://localhost:8088 ，默认账号 **admin / admin123**（首次登录须完成安装向导）。

可选 AI 组件：

```bash
# 启动 Ollama（本地大模型 + Embedding）
docker compose -f docker-compose.full.yml --profile ai up -d

# 启动 Qdrant（VECTOR_PROVIDER=qdrant 时）
docker compose -f docker-compose.full.yml --profile qdrant up -d
```

### 方式 B：本地开发

```powershell
cd docker; docker compose up -d   # MySQL（Redis 可选）
cd ..
scripts/dev.ps1                   # 或分别启动 server + web
```

- 前端：http://localhost:5174
- API：http://localhost:8088/api
- Swagger：http://localhost:8088/api/swagger-ui.html
- 默认 `CACHE_PROVIDER=caffeine`，**无需 Redis** 即可启动后端

**单 Jar 打包：**

```powershell
scripts/build.ps1
# 或：mvn -pl apps/server -am clean package -DskipTests -Pbundle
```

---

## 环境变量

关键配置见 [docker/.env.example](docker/.env.example)：

```bash
VECTOR_PROVIDER=lucene          # lucene | pgvector | qdrant
CACHE_PROVIDER=caffeine         # caffeine | redis
EMBEDDING_PROVIDER=onnx         # onnx | ollama | hash
LLM_BASE_URL=http://localhost:11434/v1
LLM_MODEL=qwen2.5:7b
LLM_ALLOW_EXTERNAL=true         # false = 禁止外连大模型
```

---

## Web 功能入口

| 页面 | 路径 | 说明 |
|------|------|------|
| 概览 | `/dashboard` | 知识库与文档统计、审计日志 |
| 知识库 | `/kbs` | 创建、管理知识库与文档 |
| 智能检索 | `/search` | 混合语义检索 |
| 智能问答 | `/qa` | RAG 单轮问答 |
| 智能对话 | `/chat` | 多轮流式对话 |
| 智能写文档 | `/writer` | AI 生成 Markdown |
| 用户管理 | `/users` | 管理员专用 |

---

## 许可证

Fast Knowledge 采用 **AGPL-3.0 + 商业双许可** 发布：

- **开源许可**：[GNU Affero General Public License v3.0](LICENSE)（AGPL-3.0）
- **商业许可**：若您将本项目用于 SaaS、闭源集成、OEM 分发等不愿遵守 AGPL 开源义务的场景，请阅读 [LICENSE-COMMERCIAL.md](LICENSE-COMMERCIAL.md) 并联系我们获取商业授权。

在符合 AGPL 的前提下（如企业内部私有化部署、或愿意按要求开源修改），可免费使用，无需额外付费。

**Topics**：`knowledge-base` · `rag` · `semantic-search` · `self-hosted` · `spring-boot` · `vue3` · `langchain4j` · `docker` · `enterprise-search` · `agpl-3.0`

---

## CI / CD

GitHub Actions 已配置持续集成与交付流水线（见 [`.github/workflows/`](.github/workflows/)）：

| 工作流 | 触发 | 说明 |
|--------|------|------|
| **CI** | `push` / `pull_request` → `main` | 后端 `mvn test` + 打包、前端 typecheck / lint / test / build、Docker 镜像构建校验 |
| **CD** | `push` → `main` 或标签 `v*` | 构建并推送镜像到 GHCR，打 `v*` 标签时自动创建 GitHub Release |

**拉取预构建镜像（CD 成功后可用）：**

```bash
docker pull ghcr.io/lixuanqun/fast-knowledge:latest
```

首次拉取私有包需登录：`echo $GITHUB_TOKEN | docker login ghcr.io -u USERNAME --password-stdin`

查看流水线状态：[Actions](https://github.com/lixuanqun/fast-knowledge/actions)
