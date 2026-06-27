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
: "${MQTT_BROKER:=tcp://localhost:33405}"
: "${TCP_PORT:=33410}"
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-dev}"

# 环境检查
command -v java >/dev/null 2>&1 || { echo "❌ 未找到 java,请安装 JDK 17"; exit 1; }
command -v mvn   >/dev/null 2>&1 || { echo "❌ 未找到 mvn,请安装 Maven 3.9+"; exit 1; }

cd "$BACKEND_DIR"

echo "========================================"
echo "  IoT Platform - 启动后端"
echo "========================================"
echo "  Profile:      $SPRING_PROFILES_ACTIVE"
echo "  MySQL:        $MYSQL_HOST:$MYSQL_PORT"
echo "  TDengine:     $TDENGINE_HOST:$TDENGINE_PORT"
echo "  MQTT Broker:  $MQTT_BROKER"
echo "  TCP Server:   $TCP_PORT"
echo "  Backend Port: $BACKEND_PORT"
echo "========================================"
echo

exec mvn spring-boot:run \
  -Dspring-boot.run.jvmArguments="\
    -Dserver.port=$BACKEND_PORT \
    -DMYSQL_HOST=$MYSQL_HOST \
    -DMYSQL_PORT=$MYSQL_PORT \
    -DTDENGINE_HOST=$TDENGINE_HOST \
    -DTDENGINE_PORT=$TDENGINE_PORT \
    -DMQTT_BROKER=$MQTT_BROKER \
    -DTCP_PORT=$TCP_PORT"