#!/usr/bin/env bash
# =============================================================================
# Fast Knowledge — 离线包安装入口（内网服务器上执行）
# =============================================================================
# 作用：强制内网不出域配置，然后调用同目录 install.sh 完成部署。
# 用法：bash install-offline.sh [--env-file PATH] [install.sh 支持的其他选项]
# =============================================================================
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE=""
EXTRA_ARGS=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file) ENV_FILE="$2"; shift 2 ;;
    --env-file=*) ENV_FILE="${1#*=}"; shift ;;
    *) EXTRA_ARGS+=("$1"); shift ;;
  esac
done

[[ -f "$DIR/fast-knowledge.jar" ]] || { echo -e "[ERROR] 未找到 fast-knowledge.jar，请先在构建机制作离线包（scripts/offline/prepare-bundle.sh）" >&2; exit 1; }

# ---- 生成内网默认配置（若未提供）----
if [[ -z "$ENV_FILE" ]]; then
  if [[ -f "$DIR/.env" ]]; then
    ENV_FILE="$DIR/.env"
  elif [[ -f "$DIR/.env.example" ]]; then
    cp "$DIR/.env.example" "$DIR/.env"
    ENV_FILE="$DIR/.env"
    echo "[INFO] 已从模板生成 $ENV_FILE，部署前请编辑数据库等凭据"
  fi
fi

# ---- 强制不出域：云端 LLM / Rerank 一律关闭，弱 JWT 自动替换 ----
if [[ -n "$ENV_FILE" && -f "$ENV_FILE" ]]; then
  sed -i -e 's|^LLM_ALLOW_EXTERNAL=.*|LLM_ALLOW_EXTERNAL=false|' \
         -e 's|^RERANK_ENABLED=.*|RERANK_ENABLED=false|' "$ENV_FILE"
  if grep -qE '^JWT_SECRET=(fast-knowledge-jwt-secret-change-in-production.*)?$' "$ENV_FILE" \
     || ! grep -q '^JWT_SECRET=' "$ENV_FILE"; then
    jwt="$(openssl rand -hex 32 2>/dev/null || head -c 32 /dev/urandom | od -An -tx1 | tr -d ' \n')"
    if grep -q '^JWT_SECRET=' "$ENV_FILE"; then
      sed -i "s|^JWT_SECRET=.*|JWT_SECRET=${jwt}|" "$ENV_FILE"
    else
      echo "JWT_SECRET=${jwt}" >> "$ENV_FILE"
    fi
    echo "[INFO] 已生成随机 JWT_SECRET"
  fi
  echo "[INFO] 已强制内网配置: LLM_ALLOW_EXTERNAL=false, RERANK_ENABLED=false"
fi

# ---- 调用安装器 ----
bash "$DIR/install.sh" install \
  ${ENV_FILE:+--env-file "$ENV_FILE"} \
  --jar "$DIR/fast-knowledge.jar" \
  "${EXTRA_ARGS[@]+"${EXTRA_ARGS[@]}"}"
