# Docker 部署指南

Fast Knowledge 提供两种 Docker 部署方式：**仅依赖服务** 与 **全栈一键部署**。

## 前置要求

- Docker 20.10+
- Docker Compose v2
- 可用内存建议 ≥ 4GB（若启用 Ollama 本地大模型建议 ≥ 8GB）

## 方式一：全栈部署（推荐）

应用 + MySQL + Redis + Ollama 一次启动，适合中小企业试用与私有化 POC。

```bash
cd docker
docker compose -f docker-compose.full.yml up -d --build
# 或使用根目录脚本
../scripts/install.sh
```

访问：**http://localhost:8088**

默认账号：`admin` / `admin123`（首次登录须完成设置向导）

### 拉取本地大模型（首次）

```bash
docker exec -it fast-knowledge-ollama ollama pull qwen2.5:7b
docker exec -it fast-knowledge-ollama ollama pull nomic-embed-text
```

### 启用 Qdrant 向量库（可选）

```bash
VECTOR_PROVIDER=qdrant docker compose -f docker-compose.full.yml --profile qdrant up -d
```

## 方式二：仅依赖服务

仅启动 MySQL + Redis，应用在宿主机运行（开发或自定义构建）：

```bash
cd docker
docker compose up -d
cd ..
scripts/dev.ps1
```

## 环境变量

复制模板并修改：

```bash
cp docker/.env.example docker/.env
```

| 变量 | 说明 | 默认 |
|------|------|------|
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | MySQL | docker-compose 内置 |
| `REDIS_HOST` / `REDIS_PORT` | Redis | docker-compose 内置 |
| `JWT_SECRET` | JWT 密钥（≥32 字符） | 必改 |
| `LLM_PROVIDER` | 大模型预设 | `ollama` |
| `LLM_BASE_URL` / `LLM_API_KEY` / `LLM_MODEL` | 覆盖预设 | 见 LLM 文档 |
| `EMBEDDING_PROVIDER` | `onnx` / `ollama` / `hash` | `onnx` |
| `CACHE_PROVIDER` | `caffeine` / `redis` | 全栈默认 `redis` |
| `VECTOR_PROVIDER` | `lucene` / `pgvector` / `qdrant` | `lucene` |

完整 LLM 配置见 [llm-providers.md](./llm-providers.md)。

## 数据持久化

全栈 compose 使用命名卷：

| 卷 | 内容 |
|----|------|
| `mysql_data` | 业务数据库 |
| `redis_data` | 缓存 |
| `app_data` | `/data/knowledge`（uploads、lucene、models） |

备份示例：

```bash
docker run --rm -v deploy_mysql_data:/data -v $(pwd):/backup alpine \
  tar czf /backup/mysql-backup.tar.gz /data
```

## ONNX 离线 Embedding

将模型挂载到容器：

```yaml
volumes:
  - ../data/models:/app/models:ro
```

环境变量：

```env
EMBEDDING_PROVIDER=onnx
```

模型文件说明见 [data/models/README.md](../../data/models/README.md)。

## 生产建议

1. **修改** `JWT_SECRET` 与默认管理员密码
2. 使用反向代理（Nginx）配置 HTTPS
3. 限制 `CORS_ORIGINS` 为实际域名
4. 私有化场景设置 `LLM_ALLOW_EXTERNAL=false` 并仅用本地 Ollama/ONNX
5. 定期备份 `mysql_data` 与 `app_data` 卷

## 常见问题

### 应用启动失败：数据库连接超时

等待 MySQL 健康检查通过：`docker compose logs mysql`

### Flyway 迁移失败

空库首次启动会自动建表。若手动建过旧表，请对齐 [V1__baseline.sql](../../apps/server/src/main/resources/db/migration/V1__baseline.sql)。

### 问答无响应

检查 `LLM_PROVIDER` 与 API Key；Docker 全栈默认连 `ollama` 容器，请确认已拉取 `qwen2.5:7b` 等模型。Embedding 使用 `OLLAMA_EMBED_URL`（原生 API，不含 `/v1`）。

### 检索质量差

生产建议使用 `EMBEDDING_PROVIDER=onnx` 并放置 BGE 模型，或 `ollama` + `nomic-embed-text`。

## 相关脚本

| 脚本 | 平台 | 说明 |
|------|------|------|
| `scripts/install.sh` | Linux/macOS | 全栈 compose 一键安装 |
| `scripts/dev.ps1` | Windows | 本地开发 |
| `scripts/build.ps1` | Windows | 生产构建 |
