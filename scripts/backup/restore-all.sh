#!/usr/bin/env bash
# =============================================================================
# Fast Knowledge — 恢复脚本（配合 backup-all.sh 产物，Linux 裸机部署）
# =============================================================================
# 用法：
#   bash scripts/backup/restore-all.sh [--env-file PATH] <备份目录>
# 恢复后需重启应用: systemctl restart fast-knowledge
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
APP_HOME="${APP_HOME:-/opt/fast-knowledge}"
ENV_FILE=""

if [[ "${1:-}" == "--env-file" ]]; then
  ENV_FILE="$2"; shift 2
elif [[ "${1:-}" == --env-file=* ]]; then
  ENV_FILE="${1#*=}"; shift
fi
BACKUP_DIR="${1:-}"
[[ -n "$BACKUP_DIR" && -d "$BACKUP_DIR" ]] || { echo "用法: $0 [--env-file PATH] <备份目录>"; exit 1; }

if [[ -z "$ENV_FILE" ]]; then
  for c in "$APP_HOME/.env" "$ROOT/.env.ecs" "$ROOT/.env"; do
    [[ -f "$c" ]] && { ENV_FILE="$c"; break; }
  done
fi
[[ -n "$ENV_FILE" && -f "$ENV_FILE" ]] || { echo -e "[ERROR] 未找到环境变量文件（用 --env-file 指定）" >&2; exit 1; }
set -a; source "$ENV_FILE"; set +a

log()  { echo -e "[INFO]  $*"; }
warn() { echo -e "[WARN]  $*"; }
err()  { echo -e "[ERROR] $*" >&2; exit 1; }

# ---- 1. 恢复 MySQL ----
parse_db_host_port() {
  local rest="${DB_URL#jdbc:mysql://}"
  local host="${rest%%[:/]*}"
  local port=""
  if [[ "$rest" == *:* ]]; then
    port="${rest#*:}"
    port="${port%%[:/]*}"
  fi
  echo "${host:-localhost}" "${port:-3306}"
}
DB_HOST="${DB_HOST:-$(parse_db_host_port | cut -d' ' -f1)}"
DB_PORT="${DB_PORT:-$(parse_db_host_port | cut -d' ' -f2)}"
DB_NAME="${DB_NAME:-$(echo "${DB_URL#jdbc:mysql://}" | awk -F'/' '{print $2}' | cut -d'?' -f1)}"
DB_NAME="${DB_NAME:-fast_knowledge}"
SQL_FILE="$BACKUP_DIR/${DB_NAME}.sql"

command -v mysql >/dev/null 2>&1 || err "未找到 mysql 客户端。安装: dnf install -y mysql / apt install -y mysql-client"
[[ -f "$SQL_FILE" ]] || err "未找到 $SQL_FILE（备份不完整?）"

log "恢复 MySQL: $SQL_FILE → $DB_NAME"
mysql -h"$DB_HOST" -P"$DB_PORT" \
  -u"${DB_USER:-root}" -p"${DB_PASSWORD:-}" \
  --default-character-set=utf8mb4 "$DB_NAME" < "$SQL_FILE"

# ---- 2. 恢复对象存储 ----
if [[ "${STORAGE_PROVIDER:-minio}" == "minio" ]]; then
  if [[ -d "$BACKUP_DIR/minio" ]] && command -v mc >/dev/null 2>&1; then
    log "恢复 MinIO（mc mirror）..."
    alias_name="fk_restore_$$"
    mc alias set "$alias_name" "${MINIO_ENDPOINT:-http://localhost:9000}" \
      "${MINIO_ACCESS_KEY:-minioadmin}" "${MINIO_SECRET_KEY:-minioadmin}" >/dev/null
    mc mirror --overwrite "$BACKUP_DIR/minio" "$alias_name/${MINIO_BUCKET:-fast-knowledge}"
    mc alias remove "$alias_name" >/dev/null 2>&1 || true
  elif [[ -f "$BACKUP_DIR/minio_data.tar.gz" && -d "$APP_HOME/runtime/minio-data" ]]; then
    log "恢复本机 MinIO 数据目录..."
    warn "将清空并覆盖 $APP_HOME/runtime/minio-data"
    systemctl stop minio 2>/dev/null || true
    rm -rf "$APP_HOME/runtime/minio-data"
    tar -xzf "$BACKUP_DIR/minio_data.tar.gz" -C "$APP_HOME/runtime"
    chown -R minio-user:minio-user "$APP_HOME/runtime/minio-data" 2>/dev/null || true
    systemctl start minio 2>/dev/null || true
  else
    warn "未发现 MinIO 备份数据，跳过"
  fi
fi

# ---- 3. 恢复向量索引 ----
if [[ -f "$BACKUP_DIR/vectors.tar.gz" ]]; then
  VECTOR_DIR="${VECTOR_LOCAL_DIR:-$APP_HOME/runtime/vectors}"
  log "恢复向量索引 → $VECTOR_DIR"
  mkdir -p "$(dirname "$VECTOR_DIR")"
  tar -xzf "$BACKUP_DIR/vectors.tar.gz" -C "$(dirname "$VECTOR_DIR")"
  chown -R "${APP_USER:-fastknowledge}" "$(dirname "$VECTOR_DIR")" 2>/dev/null || true
fi

log "恢复完成。重启应用: systemctl restart fast-knowledge"
