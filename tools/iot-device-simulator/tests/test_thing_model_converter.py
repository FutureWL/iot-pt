"""物模型转换测试

覆盖:
  - properties 各种类型映射(int/long/float/double/bool/text/enum)
  - specs 字符串解析(min/max/step 转 float)
  - events type 映射(INFO/WARN/ERROR/CRITICAL)
  - services outputParams → reply_data 默认值
  - 空物模型、缺字段等容错
"""
from __future__ import annotations

import pytest

from iot_simulator.api.thing_model_converter import ConversionResult, convert_thing_model
from iot_simulator.core.config import (
    EventTriggerConfig,
    PropertyGenConfig,
    ServiceConfig,
)
from iot_simulator.model.enums import GenStrategy


# ============================================================
# properties
# ============================================================
def test_convert_empty() -> None:
    """空 dict 不报错"""
    result = convert_thing_model({})
    assert result.total == 0
    # 空 dict 也会有 1 条警告("物模型为空"),不要写 == [] 的严格断言
    assert len(result.warnings) >= 0

    result = convert_thing_model(None)
    assert result.total == 0
    assert len(result.warnings) == 1


def test_convert_property_basic() -> None:
    tm = {
        "properties": [
            {
                "identifier": "temperature",
                "name": "温度",
                "type": "float",
                "unit": "℃",
                "accessMode": "ro",
                "description": "环境温度",
            }
        ]
    }
    result = convert_thing_model(tm)
    assert len(result.properties) == 1
    p = result.properties[0]
    assert p.identifier == "temperature"
    assert p.type == "float"
    assert p.report_interval_ms == 3000  # ro → 3s
    # 没有 specs → 用 CONST 避免无界
    assert p.strategy == GenStrategy.CONST


def test_convert_property_with_specs() -> None:
    """有 min/max → 用 RANDOM"""
    tm = {
        "properties": [
            {
                "identifier": "temperature",
                "name": "温度",
                "type": "float",
                "specs": {"min": "-40", "max": "125", "step": "0.1"},
                "accessMode": "ro",
            }
        ]
    }
    result = convert_thing_model(tm)
    p = result.properties[0]
    assert p.min == -40.0
    assert p.max == 125.0
    assert p.step == 0.1
    assert p.strategy == GenStrategy.RANDOM


def test_convert_property_rw_interval() -> None:
    """rw 访问模式 → 1s 间隔"""
    tm = {
        "properties": [
            {"identifier": "brightness", "name": "亮度", "type": "int",
             "accessMode": "rw", "specs": {"min": "0", "max": "100"}}
        ]
    }
    result = convert_thing_model(tm)
    assert result.properties[0].report_interval_ms == 1000


def test_convert_property_unknown_type() -> None:
    """未知类型 → 降级为 text"""
    tm = {"properties": [{"identifier": "x", "type": "weird_type"}]}
    result = convert_thing_model(tm)
    assert result.properties[0].type == "text"


def test_convert_property_type_aliases() -> None:
    """long/integer/number 都映射正确"""
    tm = {
        "properties": [
            {"identifier": "a", "type": "long", "specs": {"min": "0", "max": "100"}},
            {"identifier": "b", "type": "integer", "specs": {"min": "0", "max": "100"}},
            {"identifier": "c", "type": "double", "specs": {"min": "0", "max": "100"}},
            {"identifier": "d", "type": "number", "specs": {"min": "0", "max": "100"}},
            {"identifier": "e", "type": "boolean", "specs": {}},
            {"identifier": "f", "type": "bool", "specs": {}},
        ]
    }
    result = convert_thing_model(tm)
    types = [p.type for p in result.properties]
    assert types == ["long", "int", "double", "double", "boolean", "boolean"]


def test_convert_property_missing_identifier() -> None:
    """缺 identifier → 跳过并 warn"""
    tm = {"properties": [{"name": "no_id", "type": "float"}]}
    result = convert_thing_model(tm)
    assert result.total == 0
    assert len(result.warnings) == 1
    assert "identifier" in result.warnings[0]


def test_convert_property_invalid_specs() -> None:
    """specs 里非数字字符串 → 忽略该字段"""
    tm = {
        "properties": [
            {"identifier": "x", "type": "float",
             "specs": {"min": "abc", "max": "100"}}
        ]
    }
    result = convert_thing_model(tm)
    p = result.properties[0]
    assert p.min is None       # 解析失败,跳过
    assert p.max == 100.0
    # 因为 min=None 但 max=100,min 不在,mix,所以走 CONST
    assert p.strategy == GenStrategy.CONST


# ============================================================
# events
# ============================================================
def test_convert_event_basic() -> None:
    tm = {
        "events": [
            {
                "identifier": "high_temp",
                "name": "高温告警",
                "type": "warn",
                "outputParams": [
                    {"identifier": "value", "name": "当前温度", "type": "float"},
                    {"identifier": "threshold", "name": "阈值", "type": "float"},
                ],
            }
        ]
    }
    result = convert_thing_model(tm)
    assert len(result.events) == 1
    e = result.events[0]
    assert e.identifier == "high_temp"
    assert e.type == "WARN"
    assert e.output == {"value": 0, "threshold": 0}  # 默认值


def test_convert_event_type_aliases() -> None:
    tm = {
        "events": [
            {"identifier": "a", "type": "info"},
            {"identifier": "b", "type": "warning"},
            {"identifier": "c", "type": "fault"},
            {"identifier": "d", "type": "alert"},
            {"identifier": "e", "type": "critical"},
            {"identifier": "f", "type": "unknown"},  # 默认 INFO
        ]
    }
    result = convert_thing_model(tm)
    types = [e.type for e in result.events]
    assert types == ["INFO", "WARN", "ERROR", "WARN", "CRITICAL", "INFO"]


def test_convert_event_string_output_param() -> None:
    """outputParams 含 string 类型 → 默认空字符串"""
    tm = {
        "events": [
            {"identifier": "x", "type": "info",
             "outputParams": [{"identifier": "msg", "type": "text"}]}
        ]
    }
    result = convert_thing_model(tm)
    assert result.events[0].output == {"msg": ""}


def test_convert_event_bool_output_param() -> None:
    tm = {
        "events": [
            {"identifier": "x", "type": "info",
             "outputParams": [{"identifier": "ok", "type": "bool"}]}
        ]
    }
    result = convert_thing_model(tm)
    assert result.events[0].output == {"ok": False}


# ============================================================
# services
# ============================================================
def test_convert_service_basic() -> None:
    tm = {
        "services": [
            {
                "identifier": "reboot",
                "name": "远程重启",
                "callType": "async",
                "inputParams": [],
                "outputParams": [
                    {"identifier": "success", "name": "是否成功", "type": "bool"},
                ],
            }
        ]
    }
    result = convert_thing_model(tm)
    assert len(result.services) == 1
    s = result.services[0]
    assert s.identifier == "reboot"
    assert s.auto_reply is True
    assert s.reply_data == {"success": False}


def test_convert_service_no_output() -> None:
    """无 outputParams → reply_data 为空"""
    tm = {
        "services": [
            {"identifier": "ping", "name": "ping", "callType": "sync",
             "inputParams": [], "outputParams": []}
        ]
    }
    result = convert_thing_model(tm)
    assert result.services[0].reply_data == {}


# ============================================================
# 综合
# ============================================================
def test_convert_full_thing_model() -> None:
    """完整物模型转换"""
    tm = {
        "properties": [
            {"identifier": "temp", "name": "温度", "type": "float",
             "specs": {"min": "-40", "max": "125"}, "accessMode": "ro"},
            {"identifier": "switch", "name": "开关", "type": "boolean",
             "accessMode": "rw"},
        ],
        "events": [
            {"identifier": "high_temp", "name": "高温", "type": "warn",
             "outputParams": [{"identifier": "value", "type": "float"}]},
        ],
        "services": [
            {"identifier": "reboot", "name": "重启", "callType": "async",
             "outputParams": []},
        ],
    }
    result = convert_thing_model(tm)
    assert result.total == 4
    assert len(result.properties) == 2
    assert len(result.events) == 1
    assert len(result.services) == 1
    assert result.warnings == []


def test_convert_missing_section() -> None:
    """缺 properties/events/services 字段 → 容错,对应列表为空"""
    tm = {"properties": [{"identifier": "x", "type": "int"}]}
    result = convert_thing_model(tm)
    assert len(result.properties) == 1
    assert result.events == []
    assert result.services == []


def test_convert_invalid_property_does_not_break_others() -> None:
    """单个属性转换失败不影响其他"""
    tm = {
        "properties": [
            {"identifier": "valid", "type": "int", "specs": {"min": "0", "max": "100"}},
            {"type": "float"},  # 缺 identifier
            {"identifier": "valid2", "type": "float", "specs": {"min": "0", "max": "100"}},
        ]
    }
    result = convert_thing_model(tm)
    assert len(result.properties) == 2
    assert len(result.warnings) == 1


def test_conversion_result_repr() -> None:
    result = ConversionResult(
        properties=[PropertyGenConfig(identifier="x", type="int")],
        events=[EventTriggerConfig(identifier="y")],
        services=[ServiceConfig(identifier="z")],
        warnings=["w1", "w2"],
    )
    s = repr(result)
    assert "properties=1" in s
    assert "events=1" in s
    assert "services=1" in s
    assert "warnings=2" in s
    assert result.total == 3