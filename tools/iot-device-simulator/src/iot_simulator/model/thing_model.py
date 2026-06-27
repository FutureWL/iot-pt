"""物模型 - 与 iot-pt 平台的物模型结构对齐

参考:
  iot-pt/backend/src/main/java/com/iot/platform/protocol/core/DeviceTopicConstants.java
  iot-pt/backend/src/main/java/com/iot/platform/protocol/core/DeviceMessage.java
  iot-pt/backend/src/main/java/com/iot/platform/protocol/core/MessageType.java
"""
from __future__ import annotations

import json
from typing import Any

from pydantic import BaseModel, ConfigDict, Field, field_validator

from .enums import EventType, PropertyAccessMode, PropertyDataType, ServiceCallType


class DataTypeSpec(BaseModel):
    """数据类型规范(精度/范围/单位/枚举值)"""
    model_config = ConfigDict(extra="allow", populate_by_name=True)

    min: float | None = None
    max: float | None = None
    unit: str | None = None
    precision: int | None = None
    length: int | None = None
    # enum 类型专用 - 用 Pydantic Field alias 处理 "0"/"1" 这类数字 key
    enum_value_0: str | None = Field(default=None, alias="0")
    enum_value_1: str | None = Field(default=None, alias="1")


class PropertyDef(BaseModel):
    """属性定义"""
    identifier: str
    name: str
    access_mode: PropertyAccessMode = Field(default=PropertyAccessMode.RO, alias="accessMode")
    required: bool = False
    data_type: dict[str, Any] = Field(default_factory=lambda: {"type": "text"}, alias="dataType")

    @field_validator("identifier")
    @classmethod
    def _check_identifier(cls, v: str) -> str:
        if not v or not v.replace("_", "").isalnum():
            raise ValueError(f"identifier 非法: {v!r} (只允许字母数字下划线)")
        return v


class EventOutputParam(BaseModel):
    """事件输出参数"""
    identifier: str
    name: str = ""
    data_type: dict[str, Any] = Field(default_factory=lambda: {"type": "text"}, alias="dataType")


class EventDef(BaseModel):
    """事件定义"""
    identifier: str
    name: str
    type: EventType = EventType.INFO
    output: list[EventOutputParam] = Field(default_factory=list)


class ServiceInputParam(BaseModel):
    """服务输入参数"""
    identifier: str
    name: str = ""
    data_type: dict[str, Any] = Field(default_factory=lambda: {"type": "text"}, alias="dataType")


class ServiceOutputParam(BaseModel):
    """服务输出参数"""
    identifier: str
    name: str = ""
    data_type: dict[str, Any] = Field(default_factory=lambda: {"type": "text"}, alias="dataType")


class ServiceDef(BaseModel):
    """服务定义"""
    identifier: str
    name: str
    call_type: ServiceCallType = Field(default=ServiceCallType.ASYNC, alias="callType")
    input: list[ServiceInputParam] = Field(default_factory=list)
    output: list[ServiceOutputParam] = Field(default_factory=list)


class ThingModel(BaseModel):
    """物模型顶层"""
    version: str = "1.0"
    properties: list[PropertyDef] = Field(default_factory=list)
    events: list[EventDef] = Field(default_factory=list)
    services: list[ServiceDef] = Field(default_factory=list)

    @classmethod
    def from_json(cls, raw: str) -> "ThingModel":
        """从 JSON 字符串解析(容错空模型)"""
        if not raw or not raw.strip():
            return cls()
        try:
            data = json.loads(raw)
        except json.JSONDecodeError as e:
            raise ValueError(f"物模型 JSON 解析失败: {e}") from e
        return cls.model_validate(data)

    def to_json(self, *, indent: int = 2) -> str:
        return self.model_dump_json(by_alias=True, indent=indent, exclude_none=True)

    def get_property(self, identifier: str) -> PropertyDef | None:
        return next((p for p in self.properties if p.identifier == identifier), None)

    def get_event(self, identifier: str) -> EventDef | None:
        return next((e for e in self.events if e.identifier == identifier), None)

    def get_service(self, identifier: str) -> ServiceDef | None:
        return next((s for s in self.services if s.identifier == identifier), None)

    def identifier_type_map(self) -> dict[str, PropertyDataType]:
        """identifier -> 简化类型(只取 4 种: int/float/bool/text)"""
        out: dict[str, PropertyDataType] = {}
        for p in self.properties:
            t = (p.data_type.get("type") or "text").lower()
            if t in ("int", "long"):
                out[p.identifier] = PropertyDataType.INT
            elif t in ("double", "float"):
                out[p.identifier] = PropertyDataType.DOUBLE
            elif t in ("boolean", "bool"):
                out[p.identifier] = PropertyDataType.BOOLEAN
            else:
                out[p.identifier] = PropertyDataType.TEXT
        return out


__all__ = [
    "DataTypeSpec",
    "PropertyDef",
    "EventOutputParam",
    "EventDef",
    "ServiceInputParam",
    "ServiceOutputParam",
    "ServiceDef",
    "ThingModel",
]
