#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/docker"

WEAK_JWT="fast-knowledge-jwt-secret-change-in-production-32chars"

generate_jwt_secret() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex 32
  else
  # /dev/urandom fallback when openssl is unavailable
    head -c 32 /dev/urandom | od -An -tx1 | tr -d ' \n'
  fi
}

set_env_var() {
  local key="$1"
  local value="$2"
  local file=".env"
  if grep -q "^${key}=" "$file" 2>/dev/null; then
    if [[ "$(uname)" == "Darwin" ]]; then
      sed -i '' "s|^${key}=.*|${key}=${value}|" "$file"
    else
      sed -i "s|^${key}=.*|${key}=${value}|" "$file"
    fi
  else
    echo "${key}=${value}" >> "$file"
  fi
}

ensure_jwt_secret() {
  local current=""
  if [ -f .env ]; then
    current="$(grep '^JWT_SECRET=' .env 2>/dev/null | cut -d= -f2- || true)"
  fi
  if [ -z "$current" ] || [ "$current" = "$WEAK_JWT" ]; then
    local secret
    secret="$(generate_jwt_secret)"
    set_env_var "JWT_SECRET" "$secret"
    echo "    已自动生成 JWT_SECRET（≥32 字符）"
  fi
}

ensure_llm_api_key() {
  local current=""
  if [ -f .env ]; then
    current="$(grep '^LLM_API_KEY=' .env 2>/dev/null | cut -d= -f2- || true)"
  fi
  if [ -z "$current" ]; then
    set_env_var "LLM_API_KEY" "ollama"
    echo "    已设置 LLM_API_KEY=ollama（请按实际 LLM 提供商修改 .env）"
  fi
}

echo "==> Fast Knowledge 全栈安装（Docker Compose）"
echo "    将启动 PostgreSQL、Redis、MinIO 与应用容器"

if [ ! -f .env ]; then
  cp .env.example .env 2>/dev/null || cp ../.env.example .env 2>/dev/null || true
  echo "    已从 .env.example 创建 docker/.env"
fi

ensure_jwt_secret
ensure_llm_api_key

docker compose -f docker-compose.full.yml up -d --build

echo ""
echo "安装完成。访问 http://localhost:8088"
echo "默认账号 admin / admin123（首次登录须完成设置向导）"
echo "请确认 docker/.env 中 LLM_API_KEY 与 LLM_BASE_URL 符合你的 LLM 提供商"
