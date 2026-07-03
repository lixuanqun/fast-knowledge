# Kubernetes 部署

Fast Knowledge 在 K8s 上的最小可运行清单，与 [docker-compose.full.yml](../docker/docker-compose.full.yml) 环境变量对齐。

## 文件说明

| 文件 | 说明 |
|------|------|
| `secrets.example.yaml` | Secret 模板，部署前替换占位值 |
| `dependencies.yaml` | PostgreSQL（pgvector）、Redis、MinIO |
| `deployment.yaml` | 应用 Deployment + Service |
| `ingress.yaml` | Ingress 示例（按需修改 host 与 class） |

## 部署步骤

### 1. 准备 ONNX 模型

将模型文件放入集群可访问的存储（PVC 或节点目录），路径需与 `deployment.yaml` 中 `ONNX_*` 环境变量一致（默认 `/data/knowledge/models/`）。参见 [data/models/README.md](../data/models/README.md)。

### 2. 创建 Secret

```bash
cp k8s/secrets.example.yaml k8s/secrets.yaml
# 编辑 k8s/secrets.yaml，填入真实密钥（勿提交到 Git）
kubectl apply -f k8s/secrets.yaml
```

`jwt-secret` 须 ≥32 字符，且不能使用应用内置的弱默认值。

### 3. 部署依赖与应用

```bash
kubectl apply -f k8s/dependencies.yaml
kubectl apply -f k8s/deployment.yaml
```

### 4.（可选）配置 Ingress

修改 `ingress.yaml` 中的 `host` 与 `ingressClassName` 后：

```bash
kubectl apply -f k8s/ingress.yaml
```

### 5. 使用 GHCR 镜像

将 `deployment.yaml` 中的镜像改为 CD 推送的地址，例如：

```yaml
image: ghcr.io/lixuanqun/fast-knowledge:latest
```

## 健康检查

bundle 模式下 Actuator 路径为 `/actuator/health`（非 `/api/actuator/health`）。

## 与 Docker Compose 的差异

- K8s 示例中 `SQL_INIT_MODE=never`，首次部署需确保数据库 schema 已初始化，或临时改为 `always`。
- MinIO bucket 需自行创建（或使用 Job 执行 `mc mb`），Compose 中由 `minio-init` 完成。
- 生产环境建议为 MinIO、应用模型目录使用 PVC，并将 `dependencies.yaml` 中 MinIO 的 `emptyDir` 替换为持久卷。
