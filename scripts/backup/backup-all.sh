#!/usr/bin/env bash
# 备份 PostgreSQL 与 MinIO 数据（需在部署主机执行，依赖 docker compose）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BACKUP_DIR="${1:-$ROOT/backups/$(date +%Y%m%d_%H%M%S)}"
mkdir -p "$BACKUP_DIR"

COMPOSE_FILE="${COMPOSE_FILE:-$ROOT/docker/docker-compose.full.yml}"
ENV_FILE="${ENV_FILE:-$ROOT/docker/.env}"

echo "==> 备份 PostgreSQL..."
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" exec -T postgres \
  pg_dump -U postgres fast_knowledge > "$BACKUP_DIR/fast_knowledge.sql"

echo "==> 备份 MinIO 数据卷（若使用 compose 卷 minio_data）..."
VOLUME=$(docker volume ls -q | grep minio || true)
if [ -n "$VOLUME" ]; then
  docker run --rm -v "${VOLUME}:/data" -v "$BACKUP_DIR:/backup" alpine \
    tar czf /backup/minio_data.tar.gz -C /data .
fi

cat > "$BACKUP_DIR/README.txt" <<EOF
Fast Knowledge 备份包
时间: $(date -Iseconds)
恢复: bash scripts/backup/restore-all.sh $BACKUP_DIR
EOF

echo "完成: $BACKUP_DIR"
