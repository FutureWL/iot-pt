#!/bin/bash
# 开发模式启动
set -e
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ROOT_DIR="$( dirname "$SCRIPT_DIR" )"
cd "$ROOT_DIR"

# 装开发依赖
pip install -e ".[dev]"

# 启动
python -m iot_simulator
