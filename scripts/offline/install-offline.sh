#!/usr/bin/env bash
# 在内网服务器加载镜像并启动全栈（需先运行 prepare-bundle.sh）
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"

if ! command -v docker >/dev/null; then
  echo "错误: 未安装 Docker"
  exit 1
fi

echo "==> 加载镜像..."
for tar in images/*.tar; do
  [ -f "$tar" ] || continue
  echo "  - $tar"
  docker load -i "$tar"
done

if [ ! -f .env ]; then
  if [ -f ../../.env.example ]; then
    cp ../../.env.example .env
    echo "已复制 .env.example -> .env，请编辑 JWT_SECRET 等配置"
  else
    cat > .env <<'EOF'
JWT_SECRET=please-change-this-jwt-secret-32chars-min
LLM_ALLOW_EXTERNAL=false
EMBEDDING_PROVIDER=onnx
RERANK_ENABLED=true
RERANK_PROVIDER=onnx
EOF
    echo "已生成默认 .env，请务必修改 JWT_SECRET"
  fi
fi

export LLM_ALLOW_EXTERNAL=false
export EMBEDDING_PROVIDER=onnx
export RERANK_PROVIDER=onnx

echo "==> 启动服务..."
docker compose -f docker-compose.full.yml --env-file .env up -d

echo "完成。访问 http://localhost:8088 默认账号 admin / admin123"
