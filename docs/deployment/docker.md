# Docker 部署指南

Fast Knowledge 提供 **单机（无需 Docker）** 与 **集群 Docker** 两种交付方式。

## 前置要求

- Docker 20.10+、Docker Compose v2
- 集群部署可用内存建议 ≥ 4GB

## 单机部署（无需 Docker）

```bash
mvn -pl apps/server -am package -DskipTests -Pbundle
java -jar apps/server/target/fast-knowledge-server-*.jar --spring.profiles.active=standalone,bundle
```

数据目录：`./data/`（SQLite 数据库、上传文件、ONNX 模型）。

## 集群全栈部署

```bash
cd docker
cp .env.example .env   # 配置 JWT_SECRET、LLM_API_KEY 等
docker compose -f docker-compose.full.yml up -d --build
```

访问：**http://localhost:8088**

组件：PostgreSQL（pgvector）+ Redis + MinIO + 应用。

## 集群本地开发依赖

仅启动 PostgreSQL + Redis，应用在宿主机运行：

```bash
cd docker && docker compose up -d
cp apps/server/src/main/resources/application-local.example.yml \
   apps/server/src/main/resources/application-local.yml
mvn -pl apps/server spring-boot:run -Dspring-boot.run.profiles=prod,local,bundle
```

## 环境变量（集群 prod）

| 变量 | 说明 |
|------|------|
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | PostgreSQL |
| `REDIS_HOST` / `REDIS_PORT` | Redis |
| `MINIO_ENDPOINT` / `MINIO_BUCKET` / `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | MinIO |
| `JWT_SECRET` | ≥32 字符，必改 |
| `LLM_BASE_URL` / `LLM_API_KEY` / `LLM_MODEL` | 外部 LLM API |
| `VECTOR_PROVIDER` | 默认 `pgvector` |
| `CACHE_PROVIDER` | 默认 `redis` |
| `EMBEDDING_PROVIDER` | 默认 `onnx` |

## 数据持久化

| 卷 | 内容 |
|----|------|
| `postgres_data` | 业务库 + 向量 |
| `redis_data` | 缓存 |
| `minio_data` | 上传文件 |
| `app_data` | ONNX 模型等 |

备份 PostgreSQL：

```bash
docker exec fast-knowledge-postgres pg_dump -U postgres fast_knowledge > backup.sql
```

单机备份：复制 `./data/fast-knowledge.db` 与 `uploads/` 目录。

## Schema 初始化

- 集群：自动执行 `db/schema-postgres.sql`（含 `CREATE EXTENSION vector`）
- 单机：自动执行 `db/schema-sqlite.sql`

## 生产建议

1. 修改 `JWT_SECRET` 与默认管理员密码
2. Nginx / Ingress 配置 HTTPS
3. 生产环境可继续使用自建 MinIO 集群或托管 MinIO
4. 定期备份数据库与对象存储

## 常见问题

### 数据库连接失败

检查 PostgreSQL 健康：`docker compose logs postgres`

### 问答无响应

确认 `LLM_API_KEY` 与 `LLM_BASE_URL` 正确。

### sqlite-vec 检索慢（单机）

生产 Linux 请放置 `vec0.so`，见 `apps/server/src/main/resources/native/sqlite-vec/README.md`。

## 相关脚本

| 脚本 | 说明 |
|------|------|
| `scripts/install.sh` | 集群 compose 一键安装 |
| `scripts/dev.ps1` | 单机本地开发 |
| `scripts/build.ps1` | 打包单 Jar |
