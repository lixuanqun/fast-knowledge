#!/usr/bin/env bash
# 导出 Docker 镜像与模型文件，用于气隙/内网离线部署
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT_DIR="${1:-$ROOT/dist/offline-bundle}"
mkdir -p "$OUT_DIR/images" "$OUT_DIR/models"

echo "==> 导出 Docker 镜像..."
IMAGES=(
  "postgres:16"
  "redis:7-alpine"
  "minio/minio:latest"
)
if docker image inspect ghcr.io/lixuanqun/fast-knowledge:latest >/dev/null 2>&1; then
  IMAGES+=("ghcr.io/lixuanqun/fast-knowledge:latest")
fi

for img in "${IMAGES[@]}"; do
  safe_name=$(echo "$img" | tr '/:' '__')
  echo "  - $img"
  docker save "$img" -o "$OUT_DIR/images/${safe_name}.tar"
done

echo "==> 复制 ONNX 模型（若存在）..."
if [ -d "$ROOT/data/models" ]; then
  cp -r "$ROOT/data/models/." "$OUT_DIR/models/" 2>/dev/null || true
fi

echo "==> 复制部署配置..."
cp "$ROOT/docker/docker-compose.full.yml" "$OUT_DIR/"
cp "$ROOT/scripts/offline/install-offline.sh" "$OUT_DIR/"
cp "$ROOT/docs/compliance/data-residency-checklist.md" "$OUT_DIR/" 2>/dev/null || true

cat > "$OUT_DIR/README.txt" <<'EOF'
Fast Knowledge 离线部署包

1. 将本目录复制到目标内网服务器
2. 执行: bash install-offline.sh
3. 按 data-residency-checklist.md 完成验收

需预先安装 Docker 与 Docker Compose。
EOF

echo "完成: $OUT_DIR"
