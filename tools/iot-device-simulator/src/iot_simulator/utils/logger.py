"""日志工具 - 基于 loguru"""
from __future__ import annotations

import sys
from pathlib import Path

from loguru import logger
from platformdirs import user_log_dir

_APP_NAME = "iot-device-simulator"


def setup_logger(level: str = "INFO") -> None:
    """初始化全局日志配置

    - 控制台:彩色 + 简短格式
    - 文件:详细格式 + 自动轮转(10MB x 5)
    """
    logger.remove()  # 清除默认 sink

    # 控制台
    logger.add(
        sys.stderr,
        level=level,
        format=(
            "<green>{time:HH:mm:ss.SSS}</green> | "
            "<level>{level: <7}</level> | "
            "<cyan>{name}:{function}:{line}</cyan> - "
            "<level>{message}</level>"
        ),
        colorize=True,
    )

    # 文件
    log_dir = Path(user_log_dir(_APP_NAME))
    log_dir.mkdir(parents=True, exist_ok=True)
    logger.add(
        log_dir / "simulator.log",
        level="DEBUG",
        rotation="10 MB",
        retention=5,
        encoding="utf-8",
        format=(
            "{time:YYYY-MM-DD HH:mm:ss.SSS} | "
            "{level: <7} | "
            "{name}:{function}:{line} - {message}"
        ),
    )

    logger.info("日志目录: {}", log_dir)


__all__ = ["logger", "setup_logger"]
