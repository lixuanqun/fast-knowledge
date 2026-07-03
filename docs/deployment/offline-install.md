# 离线部署指南

## 适用场景

目标服务器**无法访问公网**，或处于气隙/专网环境。

## 准备（有网环境）

```bash
# 构建或拉取应用镜像后
bash scripts/offline/prepare-bundle.sh
```

产物目录 `dist/offline-bundle/` 包含：

- `images/*.tar` — PostgreSQL、Redis、MinIO、应用镜像
- `models/` — ONNX 模型文件（需事先放入 `data/models/`）
- `docker-compose.full.yml`
- `install-offline.sh`
- `data-residency-checklist.md`

## 内网安装

```bash
# 复制整个 offline-bundle 到目标机
cd offline-bundle
bash install-offline.sh
```

## 推荐资源配置

| 规模 | CPU | 内存 | 磁盘 |
|------|-----|------|------|
| POC（< 1 万文档） | 4 核 | 16 GB | 100 GB SSD |
| 生产（< 5 万文档） | 8 核 | 32 GB | 500 GB SSD |
| 含本地 LLM（7B） | 8 核+ | 32 GB+ | +50 GB；建议 GPU |

## 企业 Profile

生产建议启用：

```bash
SPRING_PROFILES_ACTIVE=enterprise,bundle
LLM_ALLOW_EXTERNAL=false
JWT_SECRET=<至少32字符随机串>
```

详见 [data-residency-checklist.md](./data-residency-checklist.md)。
