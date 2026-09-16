# 政通环境部署对照（阿里云·呼和浩特）

Fast Knowledge 复用**政通项目**的云基础设施部署，**演示与生产同机部署在 nmg_ztgwyc3**。本文档只维护**变量映射与部署步骤**；服务器地址、账号密码等真实值一律只放在 `.env.ztgwyc1` / `.env.ztgwyc2`（已被 `.gitignore` 忽略，不进仓库），原始信息以政通内部资料《10-服务器与基础设施》为准。

## 目标服务器现状（nmg_ztgwyc3）

该机（Alibaba Cloud Linux 4，4C / 7G / 99G 盘）原部署过一套 **FastGPT v4.15.8**（Docker Compose，占用 8080/3003/9000/9001），已于 **2026-09-16 整体下线删除**（容器、数据卷、镜像、`/opt/fastgpt` 目录；compose 配置备份在服务器 `/root/fastgpt-config-backup-20260916.tar.gz`）。

当前机器状态：仅 SSH(22) 对外监听，无业务进程；可用内存约 6.6G，磁盘已用 4.2G。**FK 端口规划：演示 8088、生产 8089**（安装脚本会自动安装 JDK 21）。

## 环境与配置文件

| 环境 | 安装目录 / systemd 服务 | 端口 | 配置文件 | 数据标识 |
|------|--------------------------|------|----------|----------|
| 演示 | `/opt/fast-knowledge` / `fast-knowledge` | 8088 | `.env.ztgwyc1` | 库 `fk_dev`，Redis db `2` |
| 生产 | `/opt/fast-knowledge-prod` / `fast-knowledge-prod` | 8089 | `.env.ztgwyc2` | 库 `fk_prd`，Redis db `3` |

> 两份 env 文件含真实凭据，随交付渠道传递，禁止提交仓库或外传。

## 基础设施复用与变量映射

| 基础设施 | 复用方式 | 对应变量 |
|----------|----------|----------|
| MySQL（阿里云 RDS，与政通业务共用实例） | FK 使用**独立数据库**，与政通业务库隔离 | `DB_URL` / `DB_USER` / `DB_PASSWORD` |
| Redis（阿里云，账号模式） | 密码用「账号:密码」格式；FK 使用政通未占用的 db 号（演示 `2`、生产 `3`，政通现用 6/7/14/15） | `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` / `REDIS_DB` |
| OSS（阿里云，政通主 Bucket） | 复用既有 Bucket 与 AccessKey，FK 对象统一放 `knowledge/` 前缀下，与政通 `contract/` 等业务目录互不干扰 | `STORAGE_PROVIDER=oss` + `OSS_ENDPOINT` / `OSS_BUCKET` / `OSS_ACCESS_KEY` / `OSS_SECRET_KEY` / `OSS_REGION` / `OSS_PREFIX` |
| 大模型 | 阿里云百炼 DashScope 兼容模式（API Key 需自备） | `LLM_*` / `EMBEDDING_*` |

## 部署前置检查

1. **RDS 建库**（FK 的 `schema-mysql.sql` 只建表、不建库）：
   ```sql
   CREATE DATABASE fk_dev DEFAULT CHARACTER SET utf8mb4;
   CREATE DATABASE fk_prd DEFAULT CHARACTER SET utf8mb4;
   ```
2. **RDS 版本**：项目 schema 面向 MySQL 5.7，确认 RDS 为 5.7（若为 8.0 需先做 Schema 回归）。
3. **白名单**：RDS 与 Redis 实例白名单需包含 ztgwyc3 的内网/公网出口 IP。
4. **Redis db 占用**：与运维确认 db `2` / `3` 未被其他服务使用。
5. **安全组**：放行 8088（演示）与 8089（生产）。
6. **JDK 21**：该机未装 Java，`install.sh` 会自动安装（Alibaba Cloud Linux 走 `dnf install java-21-openjdk-headless`），无需手动操作。
7. **DashScope API Key**：填入两份 env 文件的 `EMBEDDING_API_KEY` 与 `LLM_API_KEY`。

## 部署

```bash
# 演示实例（fast-knowledge / 8088）
sudo bash scripts/install.sh install --env-file .env.ztgwyc1

# 生产实例（fast-knowledge-prod / 8089，独立目录 /opt/fast-knowledge-prod）
sudo bash scripts/install.sh install --env-file .env.ztgwyc2
```

安装脚本会创建 systemd 服务并配置开机自启，健康检查失败自动回滚。两实例服务名、目录、端口完全隔离，可独立 `update` / `rollback`。

## 部署后验证

- 演示 `http://<ECS>:8088`、生产 `http://<ECS>:8089`，首次进入安装向导并修改默认口令 `admin/admin123`
- 上传一份文档，到 OSS 控制台确认对象落在 `knowledge/` 前缀下
- `systemctl status fast-knowledge fast-knowledge-prod` 均正常；`journalctl -u fast-knowledge-prod -f` 无报错
- 问答附带引用来源，说明 RDS / Redis / OSS / DashScope 链路全部打通

## 凭据轮换

政通运维清单要求定期轮换 RDS/Redis/OSS 密钥；轮换后**同步更新两份 env 文件**并重启对应实例（`systemctl restart fast-knowledge fast-knowledge-prod`）。
