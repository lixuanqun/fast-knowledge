# Fast Knowledge — Server 模块

Java 21 + Spring Boot 3.5 知识库 API 服务。

> 仓库结构、启动与部署见根目录 [README.md](../../README.md)。

## 功能

- JWT 认证 + 知识库 ACL
- 文档上传与异步索引（Tika 解析）
- 混合检索（向量 + 全文）
- RAG 问答、多轮对话（SSE）、智能写文档

## 部署模式

| 模式 | Profile | 数据库 | 向量 | 缓存 | 文件 |
|------|---------|--------|------|------|------|
| 单机 | `standalone,bundle` | SQLite | sqlite-vec | Caffeine | 本地 |
| 集群 | `prod,bundle` | PostgreSQL | pgvector | Redis | MinIO |

## 环境要求

- **JDK 21**
- 单机：无外部中间件
- 集群：PostgreSQL（pgvector）、Redis、MinIO
- 外部 LLM API（`LLM_BASE_URL` + `LLM_API_KEY`）

## 快速启动（单机）

```powershell
mvn -pl apps/server spring-boot:run -Dspring-boot.run.profiles=standalone,bundle
```

- API：http://localhost:8088/api
- Swagger：http://localhost:8088/api/swagger-ui.html
- 默认账号：**admin / admin123**

数据目录：`./data/`（`fast-knowledge.db`、`uploads/`、`models/`）

## 集群本地开发

```bash
cd docker && docker compose up -d
cp src/main/resources/application-local.example.yml src/main/resources/application-local.yml
mvn spring-boot:run -Dspring-boot.run.profiles=prod,local,bundle
```

## 技术栈

| 组件 | 版本 |
|------|------|
| Spring Boot | 3.5.11 |
| LangChain4j | 1.17.1 |
| SQLite / PostgreSQL | sqlite-jdbc / postgresql |
| SpringDoc | 2.8.6 |

## VectorStore SPI

- `sqlite-vec` — 单机（`SqliteVecVectorStore`）
- `pgvector` — 集群（`PgVectorStore`）

## Embedding

| Provider | 说明 |
|----------|------|
| `onnx` | 生产推荐，BGE 中文模型 |
| `hash` | 开发/演示 |
| `ollama` | 可选外部服务 |

## Schema

- `src/main/resources/db/schema-sqlite.sql`
- `src/main/resources/db/schema-postgres.sql`

应用启动时 `spring.sql.init` 自动建表。
