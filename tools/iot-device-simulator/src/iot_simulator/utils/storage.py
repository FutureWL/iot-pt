"""存储工具 - 配置/日志落盘"""
from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import yaml
from platformdirs import user_config_dir, user_data_dir

_APP_NAME = "iot-device-simulator"


def config_dir() -> Path:
    """用户配置目录 (跨平台)

    - Linux: ~/.config/iot-device-simulator
    - macOS: ~/Library/Application Support/iot-device-simulator
    - Windows: %APPDATA%/iot-device-simulator
    """
    d = Path(user_config_dir(_APP_NAME))
    d.mkdir(parents=True, exist_ok=True)
    return d


def data_dir() -> Path:
    """用户数据目录 (SQLite / 缓存)"""
    d = Path(user_data_dir(_APP_NAME))
    d.mkdir(parents=True, exist_ok=True)
    return d


def save_yaml(obj: dict[str, Any], path: Path) -> None:
    """保存为 YAML"""
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as f:
        yaml.safe_dump(obj, f, allow_unicode=True, sort_keys=False, indent=2)


def load_yaml(path: Path) -> dict[str, Any]:
    """加载 YAML"""
    with path.open("r", encoding="utf-8") as f:
        return yaml.safe_load(f) or {}


def save_json(obj: Any, path: Path) -> None:
    """保存为 JSON (人类友好)"""
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)


def load_json(path: Path) -> Any:
    """加载 JSON"""
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


__all__ = [
    "config_dir",
    "data_dir",
    "save_yaml",
    "load_yaml",
    "save_json",
    "load_json",
]
