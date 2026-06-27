"""入口 - 启动 GUI"""
from __future__ import annotations

import os
import sys

# 必须在 import QApplication 之前设置高 DPI 属性
# Qt 6 默认开启 HiDPI,但 WSLg / X11 下需手动启用
os.environ.setdefault("QT_ENABLE_HIGHDPI_SCALING", "1")
# QT_SCALE_FACTOR 可被外部覆盖(例如 QT_SCALE_FACTOR=1.5)
os.environ.setdefault("QT_SCALE_FACTOR_ROUNDING_POLICY", "PassThrough")

from PySide6.QtCore import QCoreApplication
from PySide6.QtGui import QGuiApplication
from PySide6.QtWidgets import QApplication

from .ui.main_window import MainWindow
from .utils.logger import setup_logger, logger


def main() -> int:
    # Qt 应用标识(影响 QStandardPaths)
    QCoreApplication.setOrganizationName("IoT Platform")
    QCoreApplication.setApplicationName("IoT Device Simulator")
    QCoreApplication.setApplicationVersion("0.1.0")

    # 日志
    setup_logger()

    app = QApplication.instance() or QApplication(sys.argv)
    app.setStyle("Fusion")  # 跨平台统一外观

    # 启动后检查实际缩放比例
    screen = app.primaryScreen()
    ratio = screen.devicePixelRatio() if screen else 1.0
    logger.info(f"显示缩放比: {ratio}x (环境变量 QT_SCALE_FACTOR={os.environ.get('QT_SCALE_FACTOR', '未设置')})")

    win = MainWindow()
    win.show()

    return app.exec()


if __name__ == "__main__":
    sys.exit(main())
