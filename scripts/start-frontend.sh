#!/usr/bin/env bash
# ============================================================
# IoT Platform - 启动前端 (本地开发)
#
# 前置条件:
#   1. Node.js 20+ 已安装
#   2. 已运行 npm install
#
# 使用:
#   ./scripts/start-frontend.sh
# ============================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
FRONTEND_DIR="$PROJECT_ROOT/frontend"

command -v node >/dev/null 2>&1 || { echo "❌ 未找到 node,请安装 Node.js 20+"; exit 1; }
command -v npm  >/dev/null 2>&1 || { echo "❌ 未找到 npm"; exit 1; }

cd "$FRONTEND_DIR"

if [ ! -d node_modules ]; then
  echo "▶ 首次运行,执行 npm install ..."
  npm install --no-audit --no-fund
fi

echo "========================================"
echo "  IoT Platform - 启动前端 (Vite dev)"
echo "========================================"
echo "  URL: http://localhost:33411"
echo "========================================"
echo

exec npm run dev