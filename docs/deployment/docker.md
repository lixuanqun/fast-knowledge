# Docker 部署指南

Fast Knowledge 面向**中小企业私有化部署**（Single Instance、Docker First）：本地开发与生产均基于 **Docker Compose** 拉起依赖（MySQL 5.7 + Redis + MinIO），应用使用同一镜像与环境变量契约。产品定位见 [产品说明.md](../产品说明.md)。

## 前置要求

- Docker 20.10+、Docker Compose v2
- 建议可用内存 ≥ 4GB

## 本地开发

```powershell
# 启动依赖
cd docker
docker compose up -d

# 启动应用（或使用 scripts/dev.ps1 一键启动前后端）
cd ..
mvn -pl apps/server spring-boot:run -Dspring-boot.run.profiles=bundle
```

访问：

- 后端 API：http://localhost:8088/api
- Swagger：http://localhost:8088/api/swagger-ui.html
- MinIO 控制台：http://localhost:9001（minioadmin / minioadmin）

## 全栈部署（含应用镜像）

```bash
cd docker
cp ../.env.example .env   # install 脚本会自动生成 JWT_SECRET；请配置 LLM_API_KEY 等
../scripts/install.sh
# 或 Windows: ..\scripts\install.ps1
docker compose -f docker-compose.full.yml up -d --build
```

访问：**http://localhost:8088**

组件：MySQL 5.7 + Redis + MinIO + 应用。

## 环境变量

完整列表见 [.env.example](../.env.example)。常用项：

| 变量 | 说明 |
|------|------|
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | MySQL 5.7 |
| `REDIS_HOST` / `REDIS_PORT` | Redis |
| `MINIO_ENDPOINT` / `MINIO_BUCKET` / `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | MinIO |
| `MINIO_REGION` | S3 region（本地 MinIO 用 `us-east-1`，应用默认） |
| `JWT_SECRET` | ≥32 字符，生产必改 |
| `LLM_BASE_URL` / `LLM_API_KEY` / `LLM_MODEL` | 外部 LLM API |
| `EMBEDDING_PROVIDER` | 默认 `onnx` |
| `RERANK_ENABLED` / `COHERE_API_KEY` / `JINA_API_KEY` | 可选 Reranker |

## 数据持久化

| 卷 | 内容 |
|----|------|
| `mysql_data` | 业务库数据 |
| `redis_data` | 缓存、Token 黑名单、索引锁 |
| `minio_data` | 上传文件 |
| `app_data` | ONNX 模型等 |

备份 MySQL：

```bash
docker exec fast-knowledge-mysql mysqldump -uroot -proot --default-character-set=utf8mb4 fast_knowledge > backup.sql
```

## Schema 初始化

应用启动时自动执行 `db/schema-mysql.sql`。向量索引由 `LocalEmbeddingStore` 持久化为本地文件（挂载卷 `app_data:/data/knowledge/vectors`），不占数据库。

## 生产建议

1. 修改 `JWT_SECRET` 与默认管理员密码
2. Nginx / Ingress 配置 HTTPS
3. 定期备份数据库与 MinIO
4. 大规模检索可开启 `RERANK_ENABLED=true`，推荐 `RERANK_PROVIDER=onnx` 并挂载 `bge-reranker-base.onnx`

## 常见问题

### 数据库连接失败

检查 MySQL 健康：`docker compose logs mysql`

### 问答无响应

确认 `LLM_API_KEY` 与 `LLM_BASE_URL` 正确。

### Rerank 未生效

确认 `RERANK_ENABLED=true` 且对应 `COHERE_API_KEY` 或 `JINA_API_KEY` 已配置；查看启动日志是否有 `Rerank provider: ...`。

## 相关脚本

| 脚本 | 说明 |
|------|------|
| `scripts/install.sh` | Linux/macOS 集群 compose 一键安装 |
| `scripts/install.ps1` | Windows 集群 compose 一键安装 |
| `scripts/dev.ps1` | 本地开发（Docker 依赖 + 后端 + 前端） |
| `scripts/build.ps1` | 打包单 Jar |
