#!/usr/bin/env bash
# 从 backup-all.sh 产物恢复
set -euo pipefail

if [ $# -lt 1 ]; then
  echo "用法: $0 <backup_dir>"
  exit 1
fi

BACKUP_DIR="$1"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
COMPOSE_FILE="${COMPOSE_FILE:-$ROOT/docker/docker-compose.full.yml}"
ENV_FILE="${ENV_FILE:-$ROOT/docker/.env}"

if [ ! -f "$BACKUP_DIR/fast_knowledge.sql" ]; then
  echo "错误: 未找到 fast_knowledge.sql"
  exit 1
fi

echo "==> 恢复 PostgreSQL..."
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" exec -T postgres \
  psql -U postgres -d fast_knowledge < "$BACKUP_DIR/fast_knowledge.sql"

if [ -f "$BACKUP_DIR/minio_data.tar.gz" ]; then
  echo "==> 恢复 MinIO 卷..."
  VOLUME=$(docker volume ls -q | grep minio || true)
  if [ -n "$VOLUME" ]; then
    docker run --rm -v "${VOLUME}:/data" -v "$BACKUP_DIR:/backup" alpine \
      sh -c "rm -rf /data/* && tar xzf /backup/minio_data.tar.gz -C /data"
  fi
fi

echo "恢复完成，请重启应用服务。"
