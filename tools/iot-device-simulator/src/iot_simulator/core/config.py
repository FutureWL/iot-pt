"""完整配置 - 包含连接/属性/事件/服务

Pydantic v2 模型,UI 加载/保存都用这个。
"""
from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator

from ..model.enums import GenStrategy, ProtocolType, TriggerType


# ============================================================
# 连接
# ============================================================
class MqttConfig(BaseModel):
    """MQTT 连接配置"""
    model_config = ConfigDict(extra="forbid")

    host: str = "localhost"
    port: int = 1883
    client_id: str = ""            # 空 = 自动用 simulator-{device_key}
    username: str = ""
    password: str = ""
    keepalive: int = 60
    use_tls: bool = False
    qos: Literal[0, 1, 2] = 1

    @field_validator("port")
    @classmethod
    def _check_port(cls, v: int) -> int:
        if not 1 <= v <= 65535:
            raise ValueError(f"port 非法: {v}")
        return v


class TcpConfig(BaseModel):
    """TCP 连接配置(明文 JSON 行协议)"""
    model_config = ConfigDict(extra="forbid")

    host: str = "localhost"
    port: int = 33410
    connect_timeout_sec: int = 5
    read_timeout_sec: int = 60
    keepalive_sec: int = 30


# ============================================================
# 属性
# ============================================================
class PropertyGenConfig(BaseModel):
    """单个属性的生成策略"""
    model_config = ConfigDict(extra="forbid")

    identifier: str
    type: Literal["int", "long", "double", "float", "boolean", "text"] = "double"
    strategy: GenStrategy = GenStrategy.RANDOM

    # 周期 (毫秒)
    report_interval_ms: int = 1000
    # 初始值
    initial: Any = None
    # 各种策略的参数
    min: float | None = None
    max: float | None = None
    precision: int = 2

    # increment 专用
    step: float = 1.0

    # sine 专用
    base: float | None = None
    amplitude: float | None = None
    period_sec: float = 60.0


# ============================================================
# 事件
# ============================================================
class EventTriggerConfig(BaseModel):
    """单个事件的触发配置"""
    model_config = ConfigDict(extra="forbid")

    identifier: str
    type: Literal["INFO", "WARN", "ERROR", "CRITICAL"] = "INFO"

    # 触发方式
    trigger_type: TriggerType = TriggerType.MANUAL

    # 定时触发专用
    interval_sec: float = 60.0

    # 阈值触发专用
    watch_identifier: str = ""        # 监听哪个属性
    op: Literal[">", "<", ">=", "<=", "==", "!="] = ">"
    threshold: float = 0.0

    # 自定义事件输出 (identifier -> value)
    output: dict[str, Any] = Field(default_factory=dict)


# ============================================================
# 服务
# ============================================================
class ServiceConfig(BaseModel):
    """单个服务的响应配置"""
    model_config = ConfigDict(extra="forbid")

    identifier: str
    auto_reply: bool = True
    reply_delay_ms: int = 0
    # 固定的回复数据
    reply_code: int = 0
    reply_message: str = "ok"
    reply_data: dict[str, Any] = Field(default_factory=dict)


# ============================================================
# 顶层
# ============================================================
class DeviceConfig(BaseModel):
    """完整设备模拟配置"""
    model_config = ConfigDict(extra="forbid")

    # 设备身份
    product_key: str = ""
    device_key: str = ""
    device_secret: str = ""

    # 协议
    protocol: ProtocolType = ProtocolType.MQTT
    mqtt: MqttConfig = Field(default_factory=MqttConfig)
    tcp: TcpConfig = Field(default_factory=TcpConfig)

    # 物模型 JSON 字符串
    thing_model_json: str = ""

    # 属性/事件/服务
    properties: list[PropertyGenConfig] = Field(default_factory=list)
    events: list[EventTriggerConfig] = Field(default_factory=list)
    services: list[ServiceConfig] = Field(default_factory=list)

    # 全局
    report_on_change_only: bool = True
    enable_log_payload: bool = True

    @field_validator("product_key", "device_key", mode="before")
    @classmethod
    def _non_empty(cls, v: str) -> str:
        # 允许空字符串(以支持离线编辑),调用方在 start 前校验
        return v.strip() if v else v

    @field_validator("device_secret", mode="before")
    @classmethod
    def _secret(cls, v: str) -> str:
        # 不强制非空(离线编辑时可能没填)
        return v.strip() if v else v


__all__ = [
    "MqttConfig",
    "TcpConfig",
    "PropertyGenConfig",
    "EventTriggerConfig",
    "ServiceConfig",
    "DeviceConfig",
]
