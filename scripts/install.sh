#!/usr/bin/env bash
# =============================================================================
# Fast Knowledge — Linux 服务器一键部署脚本（非容器化）
# =============================================================================
# 适用系统：CentOS 7+ / RHEL / Alibaba Cloud Linux / Ubuntu 20.04+ / Debian 11+
# 架构约定：单 Jar（bundle profile）在 8088 端口同时托管前端与 API；
#           MySQL / Redis / MinIO 使用外部服务（本机自建或云托管均可）。
#
# 用法：
#   bash scripts/install.sh install [--env-file .env.ecs] [--jar app.jar]
#        [--with-nginx] [--with-local-minio] [--install-java] [--no-start]
#   bash scripts/install.sh update|rollback|start|stop|restart|status|logs
#   bash scripts/install.sh doctor          # 环境体检，不做任何变更
#   bash scripts/install.sh uninstall       # 卸载（保留 MySQL 数据）
#
# 典型首次部署：
#   cp .env.example .env.ecs && vim .env.ecs   # 填入 MySQL/MinIO/LLM 凭据
#   sudo bash scripts/install.sh install --env-file .env.ecs
# =============================================================================
set -euo pipefail

APP_NAME="${APP_NAME:-fast-knowledge}"
APP_HOME="${APP_HOME:-/opt/fast-knowledge}"
LOG_DIR="${LOG_DIR:-$APP_HOME/logs}"
RELEASE_DIR="$APP_HOME/releases"
RUNTIME_DIR="${RUNTIME_DIR:-$APP_HOME/runtime}"
VECTOR_DIR="${VECTOR_DIR:-$RUNTIME_DIR/vectors}"
APP_USER="${APP_USER:-fastknowledge}"
APP_PORT="${APP_PORT:-8088}"
JAVA_OPTS="${JAVA_OPTS:--Xms512m -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200}"
KEEP_RELEASES="${KEEP_RELEASES:-5}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-120}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE=""
JAR_FILE=""
SKIP_BUILD=0
WITH_NGINX=0
WITH_LOCAL_MINIO=0
INSTALL_JAVA=0
NO_START=0
ASSUME_YES=0

# ---------------------------------------------------------------- 输出工具 ---
if [[ -t 1 ]]; then
  RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
else
  RED=''; GREEN=''; YELLOW=''; CYAN=''; NC=''
fi
log()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC}  $*"; }
err()  { echo -e "${RED}[ERROR]${NC}  $*" >&2; exit 1; }
step() { echo -e "${CYAN}==> $*${NC}"; }

SUDO=""
if [[ "$(id -u)" -eq 0 ]]; then
  SUDO=""
elif command -v sudo >/dev/null 2>&1; then
  SUDO="sudo"
fi

confirm() {
  [[ "$ASSUME_YES" -eq 1 ]] && return 0
  read -r -p "$1 [y/N] " reply
  [[ "$reply" =~ ^[Yy]$ ]]
}

# ---------------------------------------------------------------- 参数解析 ---
CMD="install"
parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      install|update|rollback|start|stop|restart|status|logs|doctor|uninstall)
        CMD="$1"; shift ;;
      --env-file)  ENV_FILE="$2"; shift 2 ;;
      --jar)       JAR_FILE="$2"; shift 2 ;;
      --skip-build) SKIP_BUILD=1; shift ;;
      --with-nginx) WITH_NGINX=1; shift ;;
      --with-local-minio) WITH_LOCAL_MINIO=1; shift ;;
      --install-java) INSTALL_JAVA=1; shift ;;
      --no-start)  NO_START=1; shift ;;
      --yes|-y)    ASSUME_YES=1; shift ;;
      -h|--help)   usage; exit 0 ;;
      *) err "未知参数: $1（--help 查看用法）" ;;
    esac
  done
}
usage() {
  sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'
}

# ---------------------------------------------------------------- 环境配置 ---
find_env_file() {
  if [[ -n "$ENV_FILE" ]]; then
    [[ -f "$ENV_FILE" ]] || err "指定的环境变量文件不存在: $ENV_FILE"
    return
  fi
  for candidate in "$APP_HOME/.env" "$ROOT/.env.ecs" "$ROOT/.env"; do
    if [[ -f "$candidate" ]]; then
      ENV_FILE="$candidate"
      return
    fi
  done
}

gen_secret() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex 32
  else
    head -c 32 /dev/urandom | od -An -tx1 | tr -d ' \n'
  fi
}

init_env_file() {
  step "未找到环境变量文件，生成初始配置: $APP_HOME/.env"
  [[ -f "$ROOT/.env.example" ]] || err "缺少 $ROOT/.env.example，无法生成配置"
  $SUDO mkdir -p "$APP_HOME"
  $SUDO cp "$ROOT/.env.example" "$APP_HOME/.env"
  local jwt
  jwt="$(gen_secret)"
  $SUDO sed -i "s|^JWT_SECRET=.*|JWT_SECRET=${jwt}|" "$APP_HOME/.env"
  ENV_FILE="$APP_HOME/.env"
  warn "已生成随机 JWT_SECRET。请编辑 $ENV_FILE 填入 DB_PASSWORD / LLM_API_KEY / MINIO 凭据后重新执行 install"
}

load_env() {
  source_env
  if [[ "${JWT_SECRET:-}" == fast-knowledge-jwt-secret-change-in-production* || -z "${JWT_SECRET:-}" ]]; then
    err "JWT_SECRET 为空或仍是示例默认值，请在 $ENV_FILE 中修改（≥32 字符随机串）"
  fi
}

source_env() {
  find_env_file
  if [[ -z "$ENV_FILE" ]]; then
    init_env_file
    echo ""
    err "配置文件 $ENV_FILE 已生成，请编辑后重新运行: bash $0 $CMD"
  fi
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
  APP_PORT="${APP_PORT:-8088}"
}

# ---------------------------------------------------------------- 系统检测 ---
distro_pkg() {
  # 输出 yum/dnf/apt
  if command -v dnf >/dev/null 2>&1; then echo "dnf"
  elif command -v yum >/dev/null 2>&1; then echo "yum"
  elif command -v apt-get >/dev/null 2>&1; then echo "apt"
  else echo ""; fi
}

java_major_version() {
  local out
  out="$(java -version 2>&1 | head -1 | grep -oE '"[0-9]+' | tr -d '"' || true)"
  echo "${out:-0}"
}

locate_java21() {
  if command -v java >/dev/null 2>&1 && [[ "$(java_major_version)" -ge 21 ]]; then
    log "Java $(java_major_version) 已就绪: $(command -v java)"
    return 0
  fi
  local candidate
  for candidate in /usr/lib/jvm/java-21-openjdk-amd64/bin/java \
                   /usr/lib/jvm/java-21-openjdk/bin/java \
                   /usr/lib/jvm/jdk-21/bin/java \
                   /usr/local/java21/bin/java; do
    if [[ -x "$candidate" ]]; then
      export JAVA_HOME="$(dirname "$(dirname "$candidate")")"
      export PATH="$JAVA_HOME/bin:$PATH"
      log "Java 21 已找到: $candidate"
      return 0
    fi
  done
  return 1
}

ensure_java() {
  if locate_java21; then return; fi
  local pkg
  pkg="$(distro_pkg)"
  if [[ "$INSTALL_JAVA" -eq 1 && -n "$pkg" ]]; then
    step "自动安装 Java 21（$pkg）..."
    case "$pkg" in
      apt) $SUDO apt-get update -y && $SUDO apt-get install -y openjdk-21-jre-headless ;;
      *)   $SUDO "$pkg" install -y java-21-openjdk-headless ;;
    esac
    locate_java21 && return
  fi
  err "未找到 Java 21。安装命令：
      CentOS/RHEL/Alibaba:  dnf install -y java-21-openjdk-headless
      Ubuntu/Debian:        apt install -y openjdk-21-jre-headless
      或使用 --install-java 由脚本自动安装"
}

port_open() {
  timeout 3 bash -c "echo >/dev/tcp/$1/$2" 2>/dev/null
}

parse_db_host_port() {
  # 从 jdbc:mysql://host[:port]/db?... 提取 host 与 port（兼容省略端口写法）
  local rest="${DB_URL#jdbc:mysql://}"
  local host="${rest%%[:/]*}"
  local port=""
  if [[ "$rest" == *:* ]]; then
    port="${rest#*:}"
    port="${port%%[:/]*}"
  fi
  echo "${host:-localhost}" "${port:-3306}"
}

mysql_hint() {
  local host port
  read -r host port < <(parse_db_host_port)
  cat <<EOF
MySQL $host:$port 不可用。请任选其一：
  1) 云托管：阿里云 RDS MySQL（推荐，.env.example 默认建议）
  2) 本机自建：
     CentOS/Alibaba:  dnf install -y mysql-server && systemctl enable --now mysqld
     Ubuntu/Debian:   apt install -y mysql-server && systemctl enable --now mysql
  3) 确认 $ENV_FILE 中 DB_URL / DB_USER / DB_PASSWORD 正确

首次部署需初始化库表权限：
  CREATE DATABASE fast_knowledge CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
  CREATE USER 'fast_knowledge'@'%' IDENTIFIED BY '<密码>';
  GRANT ALL PRIVILEGES ON fast_knowledge.* TO 'fast_knowledge'@'%';
EOF
}

check_mysql() {
  local host port
  read -r host port < <(parse_db_host_port)
  if command -v mysql >/dev/null 2>&1 \
     && mysql -h"$host" -P"$port" -u"${DB_USER:-root}" -p"${DB_PASSWORD:-}" -e "SELECT 1" >/dev/null 2>&1; then
    log "MySQL 连接正常: $host:$port"
    return 0
  fi
  if port_open "$host" "$port"; then
    log "MySQL 端口可达: $host:$port"
    return 0
  fi
  return 1
}

check_redis() {
  local host="${REDIS_HOST:-localhost}" port="${REDIS_PORT:-6379}"
  if port_open "$host" "$port"; then
    log "Redis 端口可达: $host:$port"
    return 0
  fi
  return 1
}

check_minio() {
  local endpoint="${MINIO_ENDPOINT:-http://localhost:9000}"
  local host port
  host="$(echo "$endpoint" | awk -F'[/:]' '{print $4}')"
  port="$(echo "$endpoint" | awk -F'[/:]' '{print $5}')"
  port="${port:-9000}"
  port_open "$host" "$port"
}

minio_hint() {
  cat <<EOF
MinIO（${MINIO_ENDPOINT:-http://localhost:9000}）不可用，而 STORAGE_PROVIDER=${STORAGE_PROVIDER:-minio}。
请任选其一：
  1) 重新执行并加 --with-local-minio：脚本在本机安装并托管 MinIO（systemd）
  2) 使用外部 MinIO / 阿里云 OSS：
     - 外部 MinIO：确认 $ENV_FILE 中 MINIO_ENDPOINT / ACCESS_KEY / SECRET_KEY
     - 对象存储 OSS：STORAGE_PROVIDER=oss 并填写 OSS_* 配置
EOF
}

# ------------------------------------------------------- 本机 MinIO 托管 ---
MINIO_BIN="${MINIO_BIN:-/usr/local/bin/minio}"
setup_local_minio() {
  step "配置本机 MinIO（systemd 托管）..."
  if [[ ! -x "$MINIO_BIN" ]]; then
    if command -v curl >/dev/null 2>&1; then
      log "下载 MinIO 二进制..."
      $SUDO curl -fsSL -o "$MINIO_BIN" https://dl.min.io/server/minio/release/linux-amd64/minio
      $SUDO chmod +x "$MINIO_BIN"
    else
      err "未找到 $MINIO_BIN 且无法下载（内网请手动放置 MinIO 二进制，或用 MINIO_BIN 环境变量指定路径）"
    fi
  fi
  $SUDO id -u minio-user >/dev/null 2>&1 || $SUDO useradd -r -s /sbin/nologin minio-user
  $SUDO mkdir -p "$RUNTIME_DIR/minio-data"
  $SUDO chown -R minio-user:minio-user "$RUNTIME_DIR/minio-data"

  if [[ -z "${MINIO_ROOT_USER:-}" || "$MINIO_ROOT_USER" == "minioadmin" ]]; then
    warn "MINIO_ACCESS_KEY 仍为 minioadmin，建议修改 $ENV_FILE"
  fi
  $SUDO tee /etc/default/minio >/dev/null <<MENV
MINIO_ROOT_USER=${MINIO_ACCESS_KEY:-minioadmin}
MINIO_ROOT_PASSWORD=${MINIO_SECRET_KEY:-minioadmin}
MINIO_VOLUMES="$RUNTIME_DIR/minio-data"
MINIO_OPTS="--address :9000 --console-address :9001"
MENV
  $SUDO tee /etc/systemd/system/minio.service >/dev/null <<'MSVC'
[Unit]
Description=MinIO Object Storage
After=network.target

[Service]
Type=simple
User=minio-user
EnvironmentFile=/etc/default/minio
ExecStart=/usr/local/bin/minio server $MINIO_VOLUMES $MINIO_OPTS
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
MSVC
  $SUDO systemctl daemon-reload
  $SUDO systemctl enable --now minio
  sleep 2
  check_minio || err "MinIO 已安装但 9000 端口未就绪，请查看: journalctl -u minio -n 50"
  log "MinIO 已就绪: http://localhost:9000（控制台 :9001）"
}

# ---------------------------------------------------------------- 应用构建 ---
find_built_jar() {
  ls -t "$ROOT"/apps/server/target/fast-knowledge-server-*.jar 2>/dev/null \
    | grep -v sources | head -1 || true
}

build_jar() {
  if [[ -n "$JAR_FILE" ]]; then
    [[ -f "$JAR_FILE" ]] || err "指定的 JAR 不存在: $JAR_FILE"
    return
  fi
  if [[ "$SKIP_BUILD" -eq 1 ]]; then
    JAR_FILE="$(find_built_jar)"
    [[ -n "$JAR_FILE" ]] || err "--skip-build 但未找到已构建产物，请先执行构建或去掉 --skip-build"
    log "使用已有构建产物: $JAR_FILE"
    return
  fi
  command -v mvn >/dev/null 2>&1 || err "未找到 Maven（构建机需 mvn + Java 21）。
      无 Maven 的服务器可：本地/CI 构建后用 --jar 路径 部署现成 JAR"
  step "构建单 Jar（含前端，首次需下载依赖与 Node）..."
  (cd "$ROOT" && mvn -pl apps/server -am -DskipTests package -Pbundle -q) || err "Maven 构建失败"
  JAR_FILE="$(find_built_jar)"
  [[ -n "$JAR_FILE" ]] || err "构建完成但未找到 JAR 产物"
  log "构建完成: $JAR_FILE"
}

# ---------------------------------------------------------------- 服务管理 ---
running_under_systemd() {
  command -v systemctl >/dev/null 2>&1 && [[ -f "/etc/systemd/system/$APP_NAME.service" ]] && systemctl is-active "$APP_NAME" >/dev/null 2>&1
}

svc_stop() {
  if systemctl_list_unit; then
    $SUDO systemctl stop "$APP_NAME" 2>/dev/null || true
  elif [[ -f "$APP_HOME/app.pid" ]]; then
    local pid
    pid="$(cat "$APP_HOME/app.pid" 2>/dev/null || true)"
    [[ -n "$pid" ]] && kill "$pid" 2>/dev/null || true
    sleep 2
  fi
}

systemctl_list_unit() {
  command -v systemctl >/dev/null 2>&1 && [[ -f "/etc/systemd/system/$APP_NAME.service" ]]
}

write_systemd_unit() {
  step "注册 systemd 服务: /etc/systemd/system/$APP_NAME.service"
  $SUDO tee "/etc/systemd/system/$APP_NAME.service" >/dev/null <<SVC
[Unit]
Description=Fast Knowledge — 企业知识库
After=network.target $(systemctl_list_unit_file minio.service && echo minio.service) $(systemd_mysql_wants)

[Service]
Type=simple
User=$APP_USER
WorkingDirectory=$APP_HOME
EnvironmentFile=$APP_HOME/.env
Environment="SPRING_PROFILES_ACTIVE=bundle"
Environment="VECTOR_LOCAL_DIR=$VECTOR_DIR"
Environment="JAVA_OPTS=$JAVA_OPTS"
ExecStart=/usr/bin/env java \$JAVA_OPTS -jar $APP_HOME/$APP_NAME.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=10
StandardOutput=append:$LOG_DIR/app.log
StandardError=append:$LOG_DIR/app-error.log
LimitNOFILE=65536

[Install]
WantedBy=multi-user.target
SVC
  $SUDO systemctl daemon-reload
  $SUDO systemctl enable "$APP_NAME" >/dev/null
}

systemctl_list_unit_file() {
  [[ -f "/etc/systemd/system/$1" ]] && return 0 || return 1
}

systemd_mysql_wants() {
  systemctl_list_unit_file mysqld.service && echo mysqld.service || return 0
}

start_service() {
  if systemctl_list_unit; then
    $SUDO systemctl restart "$APP_NAME"
    log "systemd 服务已启动"
  else
    warn "无 systemd 单元（非 root 部署），使用 nohup 启动"
    (cd "$APP_HOME" && set -a && source "$APP_HOME/.env" && set +a \
      && SPRING_PROFILES_ACTIVE=bundle VECTOR_LOCAL_DIR="$VECTOR_DIR" \
      nohup java $JAVA_OPTS -jar "$APP_HOME/$APP_NAME.jar" \
        >> "$LOG_DIR/app.log" 2>> "$LOG_DIR/app-error.log" & echo $! > "$APP_HOME/app.pid")
    log "nohup 已启动（PID $(cat "$APP_HOME/app.pid")）"
  fi
}

stop_service() { svc_stop; log "服务已停止"; }

service_status() {
  if systemctl_list_unit; then
    systemctl status "$APP_NAME" --no-pager -l || true
  elif [[ -f "$APP_HOME/app.pid" ]]; then
    local pid
    pid="$(cat "$APP_HOME/app.pid")"
    if kill -0 "$pid" 2>/dev/null; then
      log "运行中（nohup PID $pid）"
    else
      warn "未运行（pid 文件存在但进程已退出）"
    fi
  else
    warn "未安装或未启动"
  fi
  if health_ok; then
    log "健康检查: OK (http://127.0.0.1:$APP_PORT)"
  else
    warn "健康检查: 无响应"
  fi
}

health_ok() {
  curl -sf -o /dev/null "http://127.0.0.1:${APP_PORT}/actuator/health" 2>/dev/null \
    || curl -sf -o /dev/null "http://127.0.0.1:${APP_PORT}/api/v1/system/config" 2>/dev/null
}

wait_health() {
  local deadline=$(( SECONDS + HEALTH_TIMEOUT ))
  step "等待服务就绪（最长 ${HEALTH_TIMEOUT}s）..."
  while (( SECONDS < deadline )); do
    if health_ok; then
      log "健康检查通过: http://127.0.0.1:$APP_PORT"
      return 0
    fi
    sleep 2
  done
  warn "健康检查超时，最近日志:"
  tail -30 "$LOG_DIR/app-error.log" 2>/dev/null || tail -30 "$LOG_DIR/app.log" 2>/dev/null || true
  return 1
}

# ---------------------------------------------------------------- 安装流程 ---
deploy_layout() {
  $SUDO mkdir -p "$APP_HOME" "$LOG_DIR" "$RELEASE_DIR" "$VECTOR_DIR"
  if ! id -u "$APP_USER" >/dev/null 2>&1; then
    step "创建系统用户: $APP_USER"
    $SUDO useradd -r -s /sbin/nologin -d "$APP_HOME" "$APP_USER" 2>/dev/null || {
      warn "无法创建系统用户，服务将以 root 运行"; APP_USER="root"; }
  fi
  $SUDO chown -R "$APP_USER":"$APP_USER" "$APP_HOME" 2>/dev/null || true
}

install_jar() {
  local ts
  ts="$(date +%Y%m%d_%H%M%S)"
  if [[ -f "$APP_HOME/$APP_NAME.jar" ]]; then
    step "备份当前版本 → releases/$APP_NAME-$ts.jar"
    $SUDO cp "$APP_HOME/$APP_NAME.jar" "$RELEASE_DIR/$APP_NAME-$ts.jar"
    local old
    old="$(ls -t "$RELEASE_DIR"/${APP_NAME}-*.jar 2>/dev/null || true)"
    local count=0
    for f in $old; do
      count=$((count + 1))
      if (( count > KEEP_RELEASES )); then $SUDO rm -f "$f"; fi
    done
  fi
  step "部署 JAR → $APP_HOME/$APP_NAME.jar"
  $SUDO cp "$JAR_FILE" "$APP_HOME/$APP_NAME.jar"
  $SUDO chown "$APP_USER":"$APP_USER" "$APP_HOME/$APP_NAME.jar"
}

sync_env_file() {
  # 运行目录必须有一份 .env（systemd EnvironmentFile 指向它）
  if [[ "$ENV_FILE" != "$APP_HOME/.env" ]]; then
    $SUDO cp "$ENV_FILE" "$APP_HOME/.env"
    log "配置已同步: $ENV_FILE → $APP_HOME/.env"
  fi
  $SUDO chown "$APP_USER":"$APP_USER" "$APP_HOME/.env" 2>/dev/null || true
}

setup_nginx() {
  command -v nginx >/dev/null 2>&1 || err "未安装 Nginx：
      CentOS/Alibaba:  dnf install -y nginx
      Ubuntu/Debian:   apt install -y nginx"
  local conf_dst
  if [[ -d /etc/nginx/conf.d ]]; then
    conf_dst=/etc/nginx/conf.d/fast-knowledge.conf
  else
    conf_dst=/etc/nginx/sites-available/fast-knowledge.conf
  fi
  step "写入 Nginx 配置: $conf_dst"
  $SUDO cp "$ROOT/deploy/nginx/fast-knowledge.conf" "$conf_dst"
  [[ "$conf_dst" == sites-available/* ]] && $SUDO ln -sf "$conf_dst" /etc/nginx/sites-enabled/fast-knowledge.conf
  $SUDO nginx -t
  $SUDO systemctl enable --now nginx >/dev/null 2>&1 || true
  $SUDO systemctl reload nginx
  log "Nginx 已启用: 80 → 127.0.0.1:$APP_PORT"
}

print_summary() {
  local ip
  ip="$(hostname -I 2>/dev/null | awk '{print $1}' || echo '<服务器IP>')"
  echo ""
  echo "=========================================="
  echo " Fast Knowledge 部署完成"
  echo "------------------------------------------"
  echo " 访问地址:  http://$ip:${APP_PORT}/"
  echo " 默认账号:  admin / admin123（请立即修改）"
  echo " 安装目录:  $APP_HOME"
  echo " 日志:      tail -f $LOG_DIR/app.log"
  echo " 服务管理:  systemctl {status|stop|start|restart} $APP_NAME"
  echo " 升级:      bash scripts/install.sh update"
  echo " 回滚:      bash scripts/install.sh rollback"
  echo "=========================================="
  echo ""
  warn "后续建议："
  echo "  1. 修改 admin 默认密码；确认 $APP_HOME/.env 中 LLM_API_KEY 已填写"
  echo "  2. 首次启动自动建表后，将 $APP_HOME/.env 中 SQL_INIT_MODE 改为 never"
  echo "  3. 开放防火墙端口（如需外部访问）:"
  echo "     firewalld: firewall-cmd --permanent --add-port=${APP_PORT}/tcp && firewall-cmd --reload"
  echo "     ufw:       ufw allow ${APP_PORT}/tcp"
}

do_install() {
  ensure_java
  load_env

  step "检查依赖服务..."
  if check_mysql; then :; else mysql_hint; exit 1; fi
  if check_redis; then :; else
    warn "Redis 不可达。可继续安装（无 Redis 时应用将降级），或在 $ENV_FILE 设置 CACHE_PROVIDER=caffeine"
  fi
  if [[ "${STORAGE_PROVIDER:-minio}" == "minio" ]]; then
    if [[ "$WITH_LOCAL_MINIO" -eq 1 ]]; then
      setup_local_minio
    elif check_minio; then
      log "MinIO 端口可达: ${MINIO_ENDPOINT:-http://localhost:9000}"
    else
      minio_hint; exit 1
    fi
  fi

  deploy_layout
  build_jar
  install_jar
  sync_env_file

  if systemctl_list_unit || [[ -n "$SUDO" || "$(id -u)" -eq 0 ]]; then
    write_systemd_unit
  else
    warn "非 root 且无 sudo，跳过 systemd 注册（将使用 nohup 启动）"
  fi

  [[ "$WITH_NGINX" -eq 1 ]] && setup_nginx

  if [[ "$NO_START" -eq 0 ]]; then
    svc_stop
    start_service
    wait_health || warn "服务未在 ${HEALTH_TIMEOUT}s 内就绪，请排查日志后重试"
  else
    log "--no-start：已部署但未启动"
  fi
  print_summary
}

do_update() {
  [[ -f "$APP_HOME/$APP_NAME.jar" ]] || err "尚未安装（先执行 install）"
  ensure_java
  build_jar
  deploy_layout
  local prev_jar="$RELEASE_DIR/previous.jar"
  step "保留当前版本用于回滚..."
  $SUDO cp "$APP_HOME/$APP_NAME.jar" "$prev_jar"

  install_jar
  sync_env_file
  svc_stop
  start_service

  if wait_health; then
    log "升级成功"
    print_summary
  else
    warn "升级后健康检查失败，自动回滚..."
    $SUDO cp "$prev_jar" "$APP_HOME/$APP_NAME.jar"
    svc_stop
    start_service
    if wait_health; then
      warn "已回滚到上一版本，服务恢复运行。请排查新版本问题"
    else
      err "回滚后仍不健康，请人工介入: journalctl -u $APP_NAME -n 100"
    fi
    exit 1
  fi
}

do_rollback() {
  local prev_jar="$RELEASE_DIR/previous.jar"
  [[ -f "$prev_jar" ]] || err "没有可回滚的版本（$prev_jar 不存在）"
  step "回滚: $prev_jar → $APP_HOME/$APP_NAME.jar"
  $SUDO cp "$prev_jar" "$APP_HOME/$APP_NAME.jar"
  svc_stop
  start_service
  wait_health && log "回滚完成" || err "回滚后健康检查失败，请人工排查"
}

do_uninstall() {
  source_env >/dev/null 2>&1 || true
  echo "将执行以下操作："
  echo "  - 停止并禁用 $APP_NAME 服务（MinIO 服务保留）"
  echo "  - 删除 /etc/systemd/system/$APP_NAME.service"
  echo "  - 删除安装目录 $APP_HOME（含日志与向量索引）"
  echo "  - MySQL 数据与 MinIO 对象不受影响"
  confirm "确认卸载?" || { log "已取消"; exit 0; }
  svc_stop
  systemctl_list_unit && $SUDO systemctl disable "$APP_NAME" >/dev/null 2>&1 || true
  $SUDO rm -f "/etc/systemd/system/$APP_NAME.service"
  command -v systemctl >/dev/null 2>&1 && $SUDO systemctl daemon-reload || true
  $SUDO rm -rf "$APP_HOME"
  log "卸载完成"
}

do_doctor() {
  step "环境体检（只读，不做变更）"
  local fail=0

  if locate_java21; then :; else
    warn "Java 21 缺失"; fail=1
  fi
  if command -v mvn >/dev/null 2>&1; then
    log "Maven: $(mvn -v 2>/dev/null | head -1)"
  else
    warn "Maven 缺失（仅在构建机上需要；部署现成 JAR 可不装）"
  fi
  if systemctl_list_unit || command -v systemctl >/dev/null 2>&1; then :; else
    warn "systemd 不可用，将以 nohup 方式运行"
  fi

  find_env_file || warn "未找到 .env（将首次生成）"
  if [[ -n "$ENV_FILE" ]]; then
    source_env
    if [[ "${JWT_SECRET:-}" == fast-knowledge-jwt-secret-change-in-production* || -z "${JWT_SECRET:-}" ]]; then
      warn "JWT_SECRET 为空或仍是示例默认值，生产使用前必须修改"
      fail=1
    fi
    step "检查数据服务（$ENV_FILE）..."
    if check_mysql; then :; else warn "MySQL 不可达"; mysql_hint; fail=1; fi
    if check_redis; then :; else
      warn "Redis 不可达（可继续：CACHE_PROVIDER=caffeine 纯本地缓存）"
    fi
    if [[ "${STORAGE_PROVIDER:-minio}" == "minio" ]] && ! check_minio; then
      warn "MinIO 不可达"; minio_hint; fail=1
    fi
    local used
    used="$(df -h "$APP_HOME" 2>/dev/null | awk 'NR==2{print $4}' || echo '?')"
    log "磁盘剩余空间（$APP_HOME）: $used"
    if [[ "${SQL_INIT_MODE:-always}" == "always" ]]; then
      warn "SQL_INIT_MODE=always：每次启动都会执行建表检查，稳定后建议改为 never"
    fi
  fi

  (( fail == 0 )) && log "体检通过 ✓" || { warn "体检发现问题（见上方 WARN/ERROR）"; exit 1; }
}

# ---------------------------------------------------------------- 入口 -------
main() {
  parse_args "$@"
  case "$CMD" in
    install)   do_install ;;
    update)    do_update ;;
    rollback)  do_rollback ;;
    start)     start_service ;;
    stop)      stop_service ;;
    restart)   svc_stop; start_service ;;
    status)    service_status ;;
    logs)      tail -n 200 -f "$LOG_DIR/app.log" ;;
    doctor)    do_doctor ;;
    uninstall) do_uninstall ;;
  esac
}

main "$@"
