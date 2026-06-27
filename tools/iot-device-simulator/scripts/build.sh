#!/bin/bash
# 打包成单可执行文件 (Linux)
set -e

APP_NAME="iot-device-simulator"
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ROOT_DIR="$( dirname "$SCRIPT_DIR" )"

cd "$ROOT_DIR"

echo "==> 清理旧构建"
rm -rf build dist *.egg-info

echo "==> 安装 PyInstaller"
pip install pyinstaller

echo "==> 开始打包"
pyinstaller \
  --name "$APP_NAME" \
  --windowed \
  --onefile \
  --noconfirm \
  --clean \
  --paths src \
  --collect-submodules PySide6 \
  --collect-submodules paho \
  --hidden-import "PySide6.QtCore" \
  --hidden-import "PySide6.QtGui" \
  --hidden-import "PySide6.QtWidgets" \
  src/iot_simulator/main.py

echo ""
echo "✅ 打包完成!"
ls -lh "dist/$APP_NAME"
echo ""
echo "运行: ./dist/$APP_NAME"
