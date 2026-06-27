"""协议消息 - 跨协议统一结构

模拟器内部用这个结构传递消息,具体协议客户端负责序列化/反序列化。
"""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


@dataclass
class TxMessage:
    """待发送消息"""
    topic: str
    payload: dict[str, Any]
    qos: int = 1


@dataclass
class RxMessage:
    """收到消息(下行)"""
    topic: str
    payload: dict[str, Any]
    raw: str = ""


@dataclass
class LogEntry:
    """实时日志条目(显示用)"""
    ts: float
    direction: str   # "TX" | "RX" | "INFO" | "ERROR"
    topic: str
    payload: Any
    level: str = "INFO"

    def short(self) -> str:
        from datetime import datetime
        ts = datetime.fromtimestamp(self.ts).strftime("%H:%M:%S.%f")[:-3]
        body = self.payload if isinstance(self.payload, str) else str(self.payload)
        if len(body) > 200:
            body = body[:200] + "..."
        return f"{ts} {self.direction:5} {self.topic} {body}"


@dataclass
class ConnState:
    """连接状态"""
    connected: bool = False
    protocol: str = ""
    host: str = ""
    port: int = 0
    error: str = ""


__all__ = ["TxMessage", "RxMessage", "LogEntry", "ConnState"]
