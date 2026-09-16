#!/usr/bin/env bash
# =============================================================================
# Fast Knowledge — 备份脚本（Linux 裸机部署：原生 mysqldump + MinIO 数据）
# =============================================================================
# 用法：
#   bash scripts/backup/backup-all.sh [--env-file PATH] [备份输出目录]
# 默认输出: /opt/fast-knowledge/backups/YYYYMMDD_HHMMSS/
# 凭据来源: --env-file 指定文件，或依次查找 $APP_HOME/.env → 仓库 .env.ecs → .env
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
BACKUP_DIR="${1:-$APP_HOME/backups/$(date +%Y%m%d_%H%M%S)}"

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

mkdir -p "$BACKUP_DIR"

# ---- 1. MySQL 逻辑备份 ----
parse_db_host_port() {
  # 从 jdbc:mysql://host[:port]/db?... 提取 host 与 port
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

command -v mysqldump >/dev/null 2>&1 || err "未找到 mysqldump。安装：
  CentOS/Alibaba:  dnf install -y mysql
  Ubuntu/Debian:   apt install -y mysql-client"

log "备份 MySQL: $DB_HOST:${DB_PORT:-3306}/$DB_NAME → ${DB_NAME}.sql"
mysqldump -h"$DB_HOST" -P"${DB_PORT:-3306}" \
  -u"${DB_USER:-root}" -p"${DB_PASSWORD:-}" \
  --single-transaction --routines --triggers --set-gtid-purged=OFF \
  "$DB_NAME" > "$BACKUP_DIR/${DB_NAME}.sql"

# ---- 2. 对象存储数据 ----
if [[ "${STORAGE_PROVIDER:-minio}" == "minio" ]]; then
  if command -v mc >/dev/null 2>&1; then
    log "备份 MinIO（mc mirror）→ minio/"
    alias_name="fk_backup_$$"
    mc alias set "$alias_name" "${MINIO_ENDPOINT:-http://localhost:9000}" \
      "${MINIO_ACCESS_KEY:-minioadmin}" "${MINIO_SECRET_KEY:-minioadmin}" >/dev/null
    mc mirror --overwrite "$alias_name/${MINIO_BUCKET:-fast-knowledge}" "$BACKUP_DIR/minio/" \
      || warn "mc mirror 失败，请检查 MinIO 凭据"
    mc alias remove "$alias_name" >/dev/null 2>&1 || true
  elif [[ -d "$APP_HOME/runtime/minio-data" ]]; then
    log "打包本机 MinIO 数据目录 → minio_data.tar.gz"
    tar -czf "$BACKUP_DIR/minio_data.tar.gz" -C "$APP_HOME/runtime" minio-data
  else
    warn "跳过 MinIO 备份：无 mc 客户端且未发现本机数据目录 $APP_HOME/runtime/minio-data"
  fi
else
  warn "STORAGE_PROVIDER=oss：OSS 数据请用 ossutil / 生命周期策略自行备份"
fi

# ---- 3. 向量索引（可选，可由文档重建）----
VECTOR_DIR="${VECTOR_LOCAL_DIR:-$APP_HOME/runtime/vectors}"
if [[ -d "$VECTOR_DIR" ]]; then
  log "打包向量索引 → vectors.tar.gz"
  tar -czf "$BACKUP_DIR/vectors.tar.gz" -C "$(dirname "$VECTOR_DIR")" "$(basename "$VECTOR_DIR")"
fi

cat > "$BACKUP_DIR/README.txt" <<EOF
Fast Knowledge 备份包
时间: $(date -Iseconds)
来源: $ENV_FILE
恢复: bash scripts/backup/restore-all.sh --env-file $ENV_FILE $BACKUP_DIR
EOF

# ---- 4. 清理过期备份（保留最近 KEEP_BACKUPS 份）----
KEEP_BACKUPS="${KEEP_BACKUPS:-14}"
old_backups="$(ls -1dt "$(dirname "$BACKUP_DIR")"/20* 2>/dev/null | tail -n +$((KEEP_BACKUPS + 1)) || true)"
if [[ -n "$old_backups" ]]; then
  while read -r d; do
    warn "清理过期备份: $d"
    rm -rf "$d"
  done <<< "$old_backups"
fi

log "备份完成: $BACKUP_DIR"
du -sh "$BACKUP_DIR" 2>/dev/null || true
