#!/usr/bin/env bash
# ============================================================
# IoT Platform - 启动后端 (本地开发)
#
# 前置条件:
#   1. JDK 17 + Maven 已安装并在 PATH 中
#   2. 已运行: make dev-infra  (或手动启动 mysql + tdengine + emqx)
#
# 使用:
#   ./scripts/start-backend.sh
#
# 自定义端口: BACKEND_PORT=33412 MYSQL_PORT=33402 ./scripts/start-backend.sh
# ============================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BACKEND_DIR="$PROJECT_ROOT/backend"

# 端口默认值(与 deploy/.env.dev / docker-compose.yml 保持一致)
: "${BACKEND_PORT:=33412}"
: "${MYSQL_HOST:=localhost}"
: "${MYSQL_PORT:=33402}"
: "${TDENGINE_HOST:=localhost}"
: "${TDENGINE_PORT:=33403}"
: "${REDIS_HOST:=localhost}"
: "${REDIS_PORT:=33413}"
: "${REDIS_PASSWORD:=}"
: "${MQTT_BROKER:=tcp://localhost:33405}"
: "${TCP_PORT:=33410}"
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-dev}"

# 环境检查
command -v java >/dev/null 2>&1 || { echo "❌ 未找到 java,请安装 JDK 17"; exit 1; }
command -v mvn   >/dev/null 2>&1 || { echo "❌ 未找到 mvn,请安装 Maven 3.9+"; exit 1; }

cd "$BACKEND_DIR"

echo "========================================"
echo "  IoT Platform - 启动后端 (本地 jar)"
echo "========================================"
echo "  Profile:      $SPRING_PROFILES_ACTIVE"
echo "  MySQL:        $MYSQL_HOST:$MYSQL_PORT"
echo "  TDengine:     $TDENGINE_HOST:$TDENGINE_PORT"
echo "  Redis:        $REDIS_HOST:$REDIS_PORT"
echo "  MQTT Broker:  $MQTT_BROKER"
echo "  TCP Server:   $TCP_PORT"
echo "  Backend Port: $BACKEND_PORT"
echo "========================================"
echo

# 优先用预先 build 好的 jar(启动快);没有才跑 mvn spring-boot:run
JAR="$BACKEND_DIR/target/iot-platform.jar"
if [ ! -f "$JAR" ] || [ -n "${REBUILD:-}" ]; then
  echo "▶ 编译打包..."
  mvn -B -q package -DskipTests
fi

exec env \
  MYSQL_HOST="$MYSQL_HOST" MYSQL_PORT="$MYSQL_PORT" \
  TDENGINE_HOST="$TDENGINE_HOST" TDENGINE_PORT="$TDENGINE_PORT" \
  REDIS_HOST="$REDIS_HOST" REDIS_PORT="$REDIS_PORT" REDIS_PASSWORD="$REDIS_PASSWORD" \
  MQTT_BROKER="$MQTT_BROKER" TCP_PORT="$TCP_PORT" \
  java -jar "$JAR" \
    --server.port=$BACKEND_PORT \
    --spring.profiles.active=$SPRING_PROFILES_ACTIVE