#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/docker"

echo "==> Fast Knowledge 一键安装"
echo "    将启动 MySQL、Redis、Ollama 与应用容器"

if [ ! -f .env ]; then
  cp .env.example .env 2>/dev/null || true
fi

docker compose -f docker-compose.full.yml up -d --build

echo ""
echo "==> 可选：拉取本地大模型（首次较慢）"
echo "    docker exec -it fast-knowledge-ollama ollama pull qwen2.5:7b"
echo "    docker exec -it fast-knowledge-ollama ollama pull nomic-embed-text"
echo ""
echo "安装完成。访问 http://localhost:8088"
echo "默认账号 admin / admin123（首次登录须完成设置向导）"
