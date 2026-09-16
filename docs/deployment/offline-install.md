# 离线部署指南

## 适用场景

目标服务器**无法访问公网**，或处于气隙/专网环境。部署形态为 Linux 服务器裸机（非容器化）。

## 一、制作离线包（有网构建机）

```bash
bash scripts/offline/prepare-bundle.sh
# 或部署现成 JAR：bash scripts/offline/prepare-bundle.sh --jar apps/server/target/fast-knowledge-server-*.jar
```

产物目录 `dist/offline-bundle/` 包含：

- `fast-knowledge.jar` — 单 Jar（含前端）
- `install.sh` — 安装器（与 `scripts/install.sh` 一致）
- `.env.example` — 配置模板
- `models/` — 本地模型文件（需事先放入 `data/models/`，若有）
- `nginx/` — Nginx 反代配置（可选）
- `data-residency-checklist.md`

内网服务器需自行具备：Java 21、MySQL 5.7+、MinIO 二进制（`--with-local-minio` 时）。

## 二、内网安装

```bash
# 整体拷贝 offline-bundle 到目标机，例如 /opt/fk-bundle
cd /opt/fk-bundle

# 1. 环境体检
bash install.sh doctor

# 2. 配置（内网纯离线模式）
cp .env.example .env && vim .env
#   LLM_PROVIDER=ollama  LLM_BASE_URL=http://<内网ollama>:11434/v1
#   LLM_ALLOW_EXTERNAL=false  RERANK_ENABLED=false

# 3. 安装（install-offline.sh 会强制不出域配置后调用 install.sh）
sudo bash install-offline.sh --env-file .env --with-local-minio
#   等价于：sudo bash install.sh install --env-file .env \
#             --jar ./fast-knowledge.jar --with-local-minio
```

`install-offline.sh` 额外做一件事：强制 `LLM_ALLOW_EXTERNAL=false`、`RERANK_ENABLED=false`，防止误配外连。

## 三、推荐资源配置

| 规模 | CPU | 内存 | 磁盘 |
|------|-----|------|------|
| POC（< 1 万文档） | 4 核 | 16 GB | 100 GB SSD |
| 生产（< 5 万文档） | 8 核 | 32 GB | 500 GB SSD |
| 含本地 LLM（7B） | 8 核+ | 32 GB+ | +50 GB；建议 GPU |

## 企业 Profile

生产建议在 `.env` 中启用：

```bash
SPRING_PROFILES_ACTIVE=enterprise,bundle
LLM_ALLOW_EXTERNAL=false
JWT_SECRET=<至少32字符随机串>
```

详见 [data-residency-checklist.md](./data-residency-checklist.md)。
