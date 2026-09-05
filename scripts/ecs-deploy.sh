#!/usr/bin/env bash
# =============================================================================
# Fast Knowledge — ECS 快速部署脚本（单机 / 裸金属 / 非容器化）
# =============================================================================
# 适用场景：
#   阿里云 ECS（CentOS 7+/Alibaba Cloud Linux 3/Ubuntu 20.04+），
#   已有外部 MySQL 5.7+ / Redis 7+ / MinIO（或阿里云 OSS）。
#
# 用法：
#   chmod +x ecs-deploy.sh
#   cp .env.example .env.ecs   # 编辑 .env.ecs 填入真实凭据
#   ./ecs-deploy.sh .env.ecs
#
# 首次部署会全量构建（含前端），后续更新执行相同命令即可（增量构建）。
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="${1:-$ROOT/.env.ecs}"
APP_NAME="fast-knowledge"
APP_PORT="${APP_PORT:-8088}"
APP_HOME="${APP_HOME:-/opt/$APP_NAME}"
LOG_DIR="${LOG_DIR:-$APP_HOME/logs}"
JAVA_OPTS="${JAVA_OPTS:--Xms512m -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200}"
RUNTIME_DIR="${RUNTIME_DIR:-$APP_HOME/runtime}"
VECTOR_DIR="${VECTOR_DIR:-$RUNTIME_DIR/vectors}"
MINIO_DIR="${MINIO_DIR:-$RUNTIME_DIR/minio-data}"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
log()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC}  $*"; }
err()  { echo -e "${RED}[ERROR]${NC} $*"; exit 1; }

# ---- 1. 加载环境变量 ----
if [ ! -f "$ENV_FILE" ]; then
    err "环境变量文件不存在: $ENV_FILE。请先 cp .env.example $ENV_FILE 并编辑凭据。"
fi
set -a; source "$ENV_FILE"; set +a
log "已加载环境变量: $ENV_FILE"

# ---- 2. Java 21 检查 ----
require_java21() {
    if command -v java >/dev/null 2>&1; then
        local ver
        ver=$(java -version 2>&1 | head -1 | grep -oP '"\K[0-9]+')
        if [ "$ver" -ge 21 ]; then
            log "Java $ver 已就绪: $(command -v java)"
            return
        fi
    fi
    # 尝试常见安装路径
    for candidate in /usr/lib/jvm/java-21-openjdk/bin/java \
                     /usr/lib/jvm/jdk-21/bin/java \
                     /usr/local/java21/bin/java \
                     "$JAVA_HOME/bin/java"; do
        if [ -x "$candidate" ]; then
            export JAVA_HOME="$(dirname "$(dirname "$candidate")")"
            export PATH="$JAVA_HOME/bin:$PATH"
            log "Java 21 已找到: $candidate"
            return
        fi
    done
    err "未找到 Java 21。请安装：yum install -y java-21-openjdk-devel（CentOS）或 apt install -y openjdk-21-jdk（Ubuntu）"
}
require_java21

# ---- 3. 基础依赖检查 ----
parse_db_host_port() {
    # 从 JDBC URL 提取 host:port（如 jdbc:mysql://host:3306/db?...）
    local url="${DB_URL:-}"
    local host="${DB_HOST:-}"
    local port="${DB_PORT:-}"
    if [ -z "$host" ] && [ -n "$url" ]; then
        host=$(echo "$url" | awk -F'[/:]' '{print $4}')
        port=$(echo "$url" | awk -F'[/:]' '{print $5}')
    fi
    echo "${host:-localhost}" "${port:-3306}"
}
require_mysql() {
    read -r host port < <(parse_db_host_port)
    if command -v mysql >/dev/null 2>&1; then
        if mysql -h"$host" -P"$port" -u"${DB_USER:-root}" -p"${DB_PASSWORD:-root}" -e "SELECT 1" >/dev/null 2>&1; then
            log "MySQL 连接正常: $host:$port"
            return
        fi
    fi
    if timeout 3 bash -c "echo >/dev/tcp/$host/$port" 2>/dev/null; then
        log "MySQL 端口可达: $host:$port"
    else
        warn "MySQL $host:$port 不可达，请确认 DB_URL 配置正确"
    fi
}
require_redis() {
    local host="${REDIS_HOST:-localhost}"
    local port="${REDIS_PORT:-6379}"
    if timeout 3 bash -c "echo >/dev/tcp/$host/$port" 2>/dev/null; then
        log "Redis 端口可达: $host:$port"
    else
        warn "Redis $host:$port 不可达。若不需要缓存可将 knowledge.cache.provider 设为 caffeine"
    fi
}
require_mysql
require_redis

# ---- 4. 创建运行时目录 ----
mkdir -p "$APP_HOME" "$LOG_DIR" "$RUNTIME_DIR" "$VECTOR_DIR" "$MINIO_DIR"
log "运行时目录已创建: $APP_HOME"

# ---- 5. 构建（含前端） ----
log "开始构建（mvn package -pl apps/server -am）…"
cd "$ROOT"
# 前端构建环境变量（VITE_ROUTER_BASE 与后端 context-path 对应）
export VITE_ROUTER_BASE="${VITE_ROUTER_BASE:-/api/v1/}"
mvn -pl apps/server -am -DskipTests package -q 2>&1 | tail -5
ARTIFACT=$(ls -t apps/server/target/fast-knowledge-server-*.jar 2>/dev/null | grep -v sources | head -1)
if [ -z "$ARTIFACT" ]; then
    err "构建失败，未找到 JAR 产物"
fi
log "构建完成: $ARTIFACT"

# 复制 JAR 到运行目录
cp "$ARTIFACT" "$APP_HOME/$APP_NAME.jar"
log "JAR 已部署到 $APP_HOME/$APP_NAME.jar"

# ---- 6. 生成 systemd 服务文件 ----
SERVICE_FILE="/etc/systemd/system/$APP_NAME.service"
cat > /tmp/${APP_NAME}.service <<SVC
[Unit]
Description=Fast Knowledge — 企业知识库
After=network.target

[Service]
Type=simple
User=${APP_USER:-root}
WorkingDirectory=$APP_HOME
EnvironmentFile=$ENV_FILE
Environment="SPRING_PROFILES_ACTIVE=bundle"
Environment="VECTOR_LOCAL_DIR=$VECTOR_DIR"
Environment="JAVA_OPTS=$JAVA_OPTS"
ExecStart=/usr/bin/env java \$JAVA_OPTS -jar $APP_HOME/$APP_NAME.jar
Restart=on-failure
RestartSec=10
StandardOutput=append:$LOG_DIR/app.log
StandardError=append:$LOG_DIR/app-error.log

[Install]
WantedBy=multi-user.target
SVC

if [ "$(id -u)" -eq 0 ]; then
    cp /tmp/${APP_NAME}.service "$SERVICE_FILE"
    systemctl daemon-reload
    log "systemd 服务已注册: $SERVICE_FILE"
else
    warn "非 root 用户，跳过 systemd 注册。服务文件已生成: /tmp/${APP_NAME}.service"
    warn "请手动执行: sudo cp /tmp/${APP_NAME}.service $SERVICE_FILE && sudo systemctl daemon-reload"
fi

# ---- 7. 启动 / 重启 ----
if [ -f "$SERVICE_FILE" ] && [ "$(id -u)" -eq 0 ]; then
    systemctl stop  $APP_NAME 2>/dev/null || true
    systemctl start $APP_NAME
    sleep 4
    systemctl status $APP_NAME --no-pager -l 2>&1 | head -12
    echo ""
    log "服务已启动。访问 http://$(hostname -I 2>/dev/null | awk '{print $1}' || echo 'ECS_IP'):$APP_PORT/api/v1/"
    log "默认账号 admin / admin123（首次登录须完成设置向导）"
else
    # 非 root 或无 systemd → nohup 启动
    warn "使用 nohup 模式启动（非 systemd）"
    cd "$APP_HOME"
    export SPRING_PROFILES_ACTIVE="bundle"
    export VECTOR_LOCAL_DIR="$VECTOR_DIR"
    nohup java $JAVA_OPTS -jar $APP_HOME/$APP_NAME.jar \
        > "$LOG_DIR/app.log" 2> "$LOG_DIR/app-error.log" &
    APP_PID=$!
    echo $APP_PID > "$APP_HOME/app.pid"
    sleep 6
    if kill -0 $APP_PID 2>/dev/null; then
        log "服务已启动（PID $APP_PID）。访问 http://localhost:$APP_PORT/api/v1/"
    else
        err "启动失败，请查看日志: tail -50 $LOG_DIR/app-error.log"
    fi
fi

# ---- 8. 健康检查 ----
HEALTH_URL="http://localhost:${APP_PORT}/api/v1/system/config"
for i in $(seq 1 30); do
    if curl -sf -o /dev/null "$HEALTH_URL" 2>/dev/null; then
        log "健康检查通过: $HEALTH_URL → 200"
        break
    fi
    sleep 2
done

echo ""
echo "=========================================="
echo " Fast Knowledge ECS 部署完成"
echo " 访问地址: http://<ECS公网IP>:$APP_PORT/api/v1/"
echo " 日志:      tail -f $LOG_DIR/app.log"
echo " 停止:      ${SERVICE_FILE:+systemctl stop $APP_NAME}"
echo "=========================================="