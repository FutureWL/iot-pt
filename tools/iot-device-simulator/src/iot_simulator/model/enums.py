"""枚举类型"""
from __future__ import annotations

from enum import StrEnum


class ProtocolType(StrEnum):
    MQTT = "MQTT"
    TCP = "TCP"


class PropertyDataType(StrEnum):
    """物模型属性类型"""
    INT = "int"
    LONG = "long"
    DOUBLE = "double"
    FLOAT = "float"     # 同 double,兼容老数据
    BOOLEAN = "boolean"
    TEXT = "text"
    ENUM = "enum"


class PropertyAccessMode(StrEnum):
    """物模型属性访问模式"""
    RO = "RO"   # 只读
    RW = "RW"   # 可读写
    WO = "WO"   # 只写


class EventType(StrEnum):
    """事件类型"""
    INFO = "INFO"
    WARN = "WARN"
    ERROR = "ERROR"
    CRITICAL = "CRITICAL"


class ServiceCallType(StrEnum):
    """服务调用类型"""
    SYNC = "SYNC"     # 同步(等回复)
    ASYNC = "ASYNC"   # 异步(不等)


class GenStrategy(StrEnum):
    """属性生成策略"""
    RANDOM = "random"
    INCREMENT = "increment"
    CONST = "const"
    SINE = "sine"


class TriggerType(StrEnum):
    """事件触发类型"""
    MANUAL = "manual"          # 手动按钮
    TIMER = "timer"            # 定时
    THRESHOLD = "threshold"    # 阈值


__all__ = [
    "ProtocolType",
    "PropertyDataType",
    "PropertyAccessMode",
    "EventType",
    "ServiceCallType",
    "GenStrategy",
    "TriggerType",
]
