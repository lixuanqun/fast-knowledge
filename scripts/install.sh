#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/docker"

echo "==> Fast Knowledge 一键安装（集群版）"
echo "    将启动 PostgreSQL、Redis、MinIO 与应用容器"

if [ ! -f .env ]; then
  cp .env.example .env 2>/dev/null || true
fi

docker compose -f docker-compose.full.yml up -d --build

echo ""
echo "安装完成。访问 http://localhost:8088"
echo "默认账号 admin / admin123（首次登录须完成设置向导）"
echo "请确保 .env 中已配置 JWT_SECRET 与 LLM_API_KEY"
