#!/usr/bin/env bash
# =============================================================================
# Fast Knowledge — 离线交付包制作（气隙/内网环境，非容器化）
# =============================================================================
# 在能联网/能构建的机器上执行，产出可直接拷贝到内网的部署包：
#   dist/offline-bundle/
#     fast-knowledge.jar       单 Jar（含前端）
#     install.sh               安装器（与仓库 scripts/install.sh 一致）
#     .env.example             配置模板
#     models/                  本地模型文件（若 data/models 存在）
#     nginx/                   Nginx 反代配置（可选）
#     data-residency-checklist.md
#
# 用法：bash scripts/offline/prepare-bundle.sh [输出目录] [--jar 现成JAR]
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT_DIR="${1:-$ROOT/dist/offline-bundle}"
JAR_FILE=""

shift_args=("$@")
i=0
while (( i < ${#shift_args[@]} )); do
  case "${shift_args[$i]}" in
    --jar) JAR_FILE="${shift_args[$((i+1))]}"; i=$((i+2)) ;;
    *) OUT_DIR="${shift_args[$i]}"; i=$((i+1)) ;;
  esac
done

log()  { echo -e "[INFO]  $*"; }
err()  { echo -e "[ERROR] $*" >&2; exit 1; }

# ---- 1. 构建（或复用现成 JAR）----
if [[ -z "$JAR_FILE" ]]; then
  log "构建单 Jar（含前端）..."
  (cd "$ROOT" && mvn -pl apps/server -am -DskipTests package -Pbundle -q) || err "Maven 构建失败"
  JAR_FILE="$(ls -t "$ROOT"/apps/server/target/fast-knowledge-server-*.jar 2>/dev/null | grep -v sources | head -1)"
fi
[[ -n "$JAR_FILE" && -f "$JAR_FILE" ]] || err "未找到 JAR 产物"

# ---- 2. 组包 ----
log "组装离线包 → $OUT_DIR"
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR/models" "$OUT_DIR/nginx"
cp "$JAR_FILE" "$OUT_DIR/fast-knowledge.jar"
cp "$ROOT/scripts/install.sh" "$OUT_DIR/install.sh"
cp "$ROOT/.env.example" "$OUT_DIR/.env.example"
cp "$ROOT/deploy/nginx/fast-knowledge.conf" "$OUT_DIR/nginx/" 2>/dev/null || true
cp "$ROOT/docs/compliance/data-residency-checklist.md" "$OUT_DIR/" 2>/dev/null || true

# 本地模型文件（可选）
if [[ -d "$ROOT/data/models" ]] && [[ -n "$(ls -A "$ROOT/data/models" 2>/dev/null)" ]]; then
  log "复制本地模型 data/models → models/"
  cp -r "$ROOT/data/models/." "$OUT_DIR/models/"
fi

cat > "$OUT_DIR/README.txt" <<'EOF'
Fast Knowledge 离线部署包（Linux 服务器，非容器化）

前置要求（内网自行准备）：
  - Linux x86_64（CentOS 7+ / Ubuntu 20.04+ 等）
  - Java 21（openjdk-21-jre / java-21-openjdk-headless）
  - MySQL 5.7+、Redis（可选）、MinIO 二进制（用 --with-local-minio 时）

部署步骤：
  1. 将本目录整体拷贝到目标服务器（如 /opt/fk-bundle）
  2. bash install.sh doctor                      # 环境体检
  3. cp .env.example .env && vim .env            # 填写 DB / MinIO / JWT_SECRET
     内网纯离线模式确认：
       LLM_PROVIDER=ollama  LLM_BASE_URL=http://<内网ollama>:11434/v1
       LLM_ALLOW_EXTERNAL=false   RERANK_ENABLED=false
  4. sudo bash install.sh install --env-file .env \
        --jar ./fast-knowledge.jar [--with-local-minio] [--with-nginx]
  5. 按 data-residency-checklist.md 完成不出域验收
EOF

log "打包完成: $OUT_DIR"
du -sh "$OUT_DIR"
