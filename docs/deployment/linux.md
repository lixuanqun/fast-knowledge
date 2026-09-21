# Linux 服务器部署指南（非容器化）

Fast Knowledge 面向**中小企业私有化部署**（Single Instance）：单 Jar（bundle profile）在 `8088` 端口同时托管前端与 API。MySQL 为必需外部服务；Redis / MinIO / OSS 均可选——单机场景可用 [极简单机模式](#六极简单机模式仅-mysql--本地盘)（仅 MySQL + 本地盘）。产品定位见 [产品说明.md](../产品说明.md)。

## 一、前置要求

| 组件 | 版本 | 说明 |
|------|------|------|
| 操作系统 | CentOS 7+ / RHEL / Alibaba Cloud Linux / Ubuntu 20.04+ / Debian 11+ | x86_64 |
| Java | 21 | 运行时必需；`--install-java` 可自动安装 |
| Maven + JDK 21 | 3.9+ | 仅**构建机**需要；部署现成 JAR 可不装 |
| MySQL | 5.7+ | 必需，推荐云数据库 RDS；首次启动自动建表 |
| Redis | 7+ | 可选；单机不用时设 `CACHE_PROVIDER=caffeine`（进程内缓存） |
| MinIO / OSS | — | 可选；`STORAGE_PROVIDER=minio\|oss\|local`，`local` 直接落本地目录，无需部署 |
| LLM | OpenAI 兼容 | DashScope / DeepSeek / 智谱 / 内网 Ollama 均可 |

资源配置参考：

| 规模 | CPU | 内存 | 磁盘 |
|------|-----|------|------|
| POC（< 1 万文档） | 4 核 | 8 GB | 100 GB SSD |
| 生产（< 5 万文档） | 8 核 | 16 GB | 500 GB SSD |

## 二、一键部署

```bash
# 1. 准备配置
cp .env.example .env.ecs
vim .env.ecs        # 必改：DB_PASSWORD、JWT_SECRET、MINIO_*、LLM_API_KEY

# 2. 环境体检（只读，检查 Java / MySQL / Redis / MinIO 连通性）
bash scripts/install.sh doctor --env-file .env.ecs

# 3. 安装并启动（构建 → /opt/fast-knowledge → systemd → 健康检查）
sudo bash scripts/install.sh install --env-file .env.ecs
```

常用可选参数：

| 参数 | 作用 |
|------|------|
| `--jar PATH` | 部署现成 JAR，跳过构建（服务器无 Maven 时） |
| `--with-nginx` | 安装 Nginx 反代配置（80 → 8088，见 [deploy/nginx](../../deploy/nginx/)） |
| `--with-local-minio` | 在本机安装 MinIO 并用 systemd 托管（单机内网方案） |
| `--install-java` | 缺 Java 21 时自动安装 |
| `--no-start` | 只部署不启动 |

安装完成后访问 `http://<服务器IP>:8088`，默认账号 `admin / admin123`（请立即修改）。

## 三、日常运维

```bash
bash scripts/install.sh status      # 服务 + 健康状态
bash scripts/install.sh logs        # 跟踪日志
bash scripts/install.sh update      # 升级：构建新 Jar，健康检查失败自动回滚
bash scripts/install.sh rollback    # 手动回滚到上一版本
bash scripts/install.sh restart
sudo bash scripts/install.sh uninstall
```

安装布局（`/opt/fast-knowledge/`）：

```
/opt/fast-knowledge/
├── fast-knowledge.jar      # 当前运行版本
├── .env                    # 运行时配置（systemd EnvironmentFile）
├── releases/               # 历史版本（回滚用，保留最近 5 个）
├── logs/                   # app.log / app-error.log
└── runtime/
    ├── vectors/            # 本地向量索引
    └── minio-data/         # 本机 MinIO 数据（--with-local-minio 时）
```

## 四、分步手动部署（不用脚本）

1. 构建单 Jar：`mvn -pl apps/server -am clean package -DskipTests -Pbundle`（产物含前端静态资源）
2. 准备 `/opt/fast-knowledge/.env`（参考 [.env.example](../../.env.example)）
3. 启动：`SPRING_PROFILES_ACTIVE=bundle java -jar fast-knowledge.jar`
4. 生产环境建议按 `scripts/install.sh` 中生成的 systemd 单元配置托管

## 五、不出域（纯内网）模式

```bash
LLM_PROVIDER=ollama
LLM_BASE_URL=http://<内网ollama>:11434/v1
LLM_ALLOW_EXTERNAL=false
RERANK_ENABLED=false            # 云端 Rerank 必须关闭
CACHE_PROVIDER=caffeine         # 无 Redis 时可选
```

验收清单见 [data-residency-checklist.md](./data-residency-checklist.md)。

## 六、极简单机模式（仅 MySQL + 本地盘）

POC、小团队或单机内网场景，可以只保留 **MySQL 一个外部依赖**：文件落本地目录、缓存用进程内 Caffeine、模型走本机 Ollama（或任一云端 API），开箱即可用。

```bash
# .env 关键项（其余按默认）
STORAGE_PROVIDER=local            # 文档存本地目录，无需 MinIO / OSS
STORAGE_LOCAL_DIR=/opt/fast-knowledge/runtime/files
CACHE_PROVIDER=caffeine           # 进程内缓存替代 Redis
LLM_PROVIDER=ollama               # 内网模型；云端 OpenAI 兼容 API 亦可
LLM_BASE_URL=http://localhost:11434/v1
LLM_API_KEY=ollama
LLM_MODEL=qwen2.5:7b
```

| 事项 | 说明 |
|------|------|
| 外部依赖 | 仅 MySQL；Redis / MinIO / OSS 全部不需要 |
| 索引调度 | 上传后即时异步索引（无 Redis 时直接调度，另有 5 分钟轮询兜底） |
| 实例数 | 仅支持单实例：会话、Token 黑名单、检索缓存只在本进程内，多实例必须回到 `CACHE_PROVIDER=redis` |
| 重启影响 | 进程重启后用户需重新登录（会话在内存中） |
| 迁移 | 存储对象键与 MinIO/OSS 同形态（`knowledge/{kbId}/…`），同步目录即可切换 provider |

## 七、常见问题

| 现象 | 排查 |
|------|------|
| 启动失败 | `journalctl -u fast-knowledge -n 100` 或 `logs/app-error.log` |
| MySQL 连接失败 | `scripts/install.sh doctor`；确认账号有 `fast_knowledge` 库权限 |
| 端口被占用 | `ss -lntp \| grep 8088`；用 `APP_PORT` 环境变量改端口 |
| 健康检查 | `curl http://127.0.0.1:8088/actuator/health` |

更多运维：[备份恢复](./backup-restore.md) · [离线安装](./offline-install.md) · [LLM 提供商](./llm-providers.md)
