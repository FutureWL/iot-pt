"""平台物模型 → 模拟器内部配置转换

把 iot-pt 平台的物模型 JSON(后端返回的结构)转换为模拟器内部的:
  - PropertyGenConfig[]
  - EventTriggerConfig[]
  - ServiceConfig[]

平台物模型结构(参考 IotProductController.detail()):
  {
    "properties": [
      {"identifier": "temperature", "name": "温度", "type": "float",
       "unit": "℃", "specs": {"min": "-40", "max": "125", "step": "0.1"},
       "accessMode": "ro", "description": "环境温度"},
      ...
    ],
    "events": [
      {"identifier": "high_temp", "name": "高温告警", "type": "warn",
       "outputParams": [{"identifier": "value", "name": "当前温度", "type": "float"}, ...]},
      ...
    ],
    "services": [
      {"identifier": "reboot", "name": "远程重启", "callType": "async",
       "inputParams": [], "outputParams": [...]},
      ...
    ]
  }
"""
from __future__ import annotations

from typing import Any

from loguru import logger

from ..core.config import (
    EventTriggerConfig,
    PropertyGenConfig,
    ServiceConfig,
)
from ..model.enums import GenStrategy, PropertyDataType, TriggerType


# 平台类型字符串 → 模拟器 PropertyDataType
_PLATFORM_TYPE_MAP: dict[str, PropertyDataType] = {
    "int": PropertyDataType.INT,
    "long": PropertyDataType.LONG,       # long 保留原样,不强制合并到 int
    "integer": PropertyDataType.INT,
    "float": PropertyDataType.FLOAT,
    "double": PropertyDataType.DOUBLE,
    "number": PropertyDataType.DOUBLE,
    "boolean": PropertyDataType.BOOLEAN,
    "bool": PropertyDataType.BOOLEAN,
    "string": PropertyDataType.TEXT,
    "text": PropertyDataType.TEXT,
    "enum": PropertyDataType.ENUM,
}


def _parse_specs(specs: dict[str, Any] | None) -> dict[str, Any]:
    """解析 specs 字段(min/max/step/precision 等)

    平台 specs 的值是字符串(便于跨语言),这里转成 float/int。
    """
    if not specs or not isinstance(specs, dict):
        return {}

    out: dict[str, Any] = {}
    for k in ("min", "max", "step", "base", "amplitude"):
        v = specs.get(k)
        if v is None or v == "":
            continue
        try:
            out[k] = float(v)
        except (ValueError, TypeError):
            logger.debug("specs.{} 不是数字,跳过: {}", k, v)

    for k in ("precision", "length"):
        v = specs.get(k)
        if v is None or v == "":
            continue
        try:
            out[k] = int(v)
        except (ValueError, TypeError):
            logger.debug("specs.{} 不是整数,跳过: {}", k, v)

    return out


def _pick_min_max(specs: dict[str, Any]) -> tuple[float | None, float | None]:
    return specs.get("min"), specs.get("max")


def _to_property(p: dict[str, Any]) -> PropertyGenConfig:
    """平台属性定义 → PropertyGenConfig

    抛出 ValueError 表示该属性应被跳过(不计入结果)
    """
    identifier = p.get("identifier") or ""
    if not identifier:
        raise ValueError(f"缺 identifier: {p}")

    # 类型映射
    plat_type = (p.get("type") or "text").lower()
    sim_type = _PLATFORM_TYPE_MAP.get(plat_type, PropertyDataType.TEXT)

    # specs(min/max/step/precision)
    specs = _parse_specs(p.get("specs"))

    # 默认 report 间隔:读访问模式,rw=短(1s),ro=长(3s)
    access_mode = (p.get("accessMode") or "ro").lower()
    interval = 1000 if access_mode == "rw" else 3000

    min_v, max_v = _pick_min_max(specs)

    # 根据类型决定默认 strategy
    if sim_type == PropertyDataType.BOOLEAN:
        strategy = GenStrategy.RANDOM
    elif sim_type in (PropertyDataType.INT, PropertyDataType.LONG,
                      PropertyDataType.FLOAT, PropertyDataType.DOUBLE):
        # 如果给了 min/max,用 RANDOM;否则用 CONST(避免无界波动)
        strategy = GenStrategy.RANDOM if (min_v is not None and max_v is not None) else GenStrategy.CONST
    else:
        strategy = GenStrategy.CONST

    return PropertyGenConfig(
        identifier=identifier,
        type=sim_type.value,         # 模拟器 type 字段是字符串字面量
        strategy=strategy,
        report_interval_ms=interval,
        initial=None,
        min=min_v,
        max=max_v,
        precision=specs.get("precision", 2),
        step=specs.get("step", 1.0),
        base=specs.get("base"),
        amplitude=specs.get("amplitude"),
        period_sec=60.0,
    )


# 平台事件 type → 模拟器 EventType(INFO/WARN/ERROR/CRITICAL)
_EVENT_TYPE_MAP: dict[str, str] = {
    "info": "INFO",
    "warn": "WARN",
    "warning": "WARN",
    "error": "ERROR",
    "critical": "CRITICAL",
    "fault": "ERROR",
    "alert": "WARN",
}


def _to_event(e: dict[str, Any]) -> EventTriggerConfig:
    """平台事件定义 → EventTriggerConfig(默认 MANUAL,用户在 UI 调整触发方式)"""
    identifier = e.get("identifier") or ""
    if not identifier:
        raise ValueError(f"缺 identifier: {e}")

    plat_type = (e.get("type") or "info").lower()
    sim_type = _EVENT_TYPE_MAP.get(plat_type, "INFO")

    # outputParams → output dict(identifier -> 默认值)
    output: dict[str, Any] = {}
    for p in e.get("outputParams") or []:
        pid = p.get("identifier")
        if not pid:
            continue
        # 默认值:数字 → 0,字符串 → 空,布尔 → False
        pt = (p.get("type") or "text").lower()
        if pt in ("int", "long", "integer", "float", "double", "number"):
            output[pid] = 0
        elif pt in ("bool", "boolean"):
            output[pid] = False
        else:
            output[pid] = ""

    return EventTriggerConfig(
        identifier=identifier,
        type=sim_type,  # type: ignore[arg-type]
        trigger_type=TriggerType.MANUAL,
        interval_sec=60.0,
        watch_identifier="",
        op=">",
        threshold=0.0,
        output=output,
    )


def _to_service(s: dict[str, Any]) -> ServiceConfig:
    """平台服务定义 → ServiceConfig"""
    identifier = s.get("identifier") or ""
    if not identifier:
        raise ValueError(f"缺 identifier: {s}")

    # outputParams → reply_data 默认值
    reply_data: dict[str, Any] = {}
    for p in s.get("outputParams") or []:
        pid = p.get("identifier")
        if not pid:
            continue
        pt = (p.get("type") or "text").lower()
        if pt in ("int", "long", "integer", "float", "double", "number"):
            reply_data[pid] = 0
        elif pt in ("bool", "boolean"):
            reply_data[pid] = False
        else:
            reply_data[pid] = ""

    return ServiceConfig(
        identifier=identifier,
        auto_reply=True,
        reply_delay_ms=0,
        reply_code=0,
        reply_message="ok",
        reply_data=reply_data,
    )


# ============================================================
# 顶层入口
# ============================================================
class ConversionResult:
    """转换结果"""

    def __init__(
        self,
        properties: list[PropertyGenConfig] | None = None,
        events: list[EventTriggerConfig] | None = None,
        services: list[ServiceConfig] | None = None,
        raw: dict[str, Any] | None = None,
        warnings: list[str] | None = None,
    ) -> None:
        self.properties = properties or []
        self.events = events or []
        self.services = services or []
        self.raw = raw or {}
        self.warnings = warnings or []

    @property
    def total(self) -> int:
        return len(self.properties) + len(self.events) + len(self.services)

    def __repr__(self) -> str:
        return (
            f"ConversionResult(properties={len(self.properties)}, "
            f"events={len(self.events)}, services={len(self.services)}, "
            f"warnings={len(self.warnings)})"
        )


def convert_thing_model(tm: dict[str, Any]) -> ConversionResult:
    """把平台物模型 dict 转换为模拟器配置

    容错:
      - 空 dict → 返回空 result(不报错)
      - 缺字段 → 跳过该项并 warn
      - 类型未知 → 用 TEXT 默认
    """
    if not tm or not isinstance(tm, dict):
        return ConversionResult(raw=tm or {}, warnings=["物模型为空"])

    warnings: list[str] = []

    # ---------- properties ----------
    properties: list[PropertyGenConfig] = []
    for p in tm.get("properties") or []:
        try:
            properties.append(_to_property(p))
        except Exception as e:
            wid = p.get("identifier") or "?"
            warnings.append(f"属性 {wid} 转换失败: {e}")
            logger.warning("属性 {} 转换失败: {}", wid, e)

    # ---------- events ----------
    events: list[EventTriggerConfig] = []
    for e in tm.get("events") or []:
        try:
            events.append(_to_event(e))
        except Exception as e:
            wid = e.get("identifier") or "?"
            warnings.append(f"事件 {wid} 转换失败: {e}")
            logger.warning("事件 {} 转换失败: {}", wid, e)

    # ---------- services ----------
    services: list[ServiceConfig] = []
    for s in tm.get("services") or []:
        try:
            services.append(_to_service(s))
        except Exception as e:
            wid = s.get("identifier") or "?"
            warnings.append(f"服务 {wid} 转换失败: {e}")
            logger.warning("服务 {} 转换失败: {}", wid, e)

    return ConversionResult(
        properties=properties,
        events=events,
        services=services,
        raw=tm,
        warnings=warnings,
    )


__all__ = ["ConversionResult", "convert_thing_model"]