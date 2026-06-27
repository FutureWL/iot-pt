"""事件触发器测试"""
from __future__ import annotations

import pytest

from iot_simulator.core.config import EventTriggerConfig
from iot_simulator.core.event_trigger import (
    ManualTrigger,
    ThresholdTrigger,
    TimerTrigger,
)
from iot_simulator.model.enums import TriggerType


def test_manual_never_auto_fires():
    t = ManualTrigger(EventTriggerConfig(identifier="e", trigger_type=TriggerType.MANUAL))
    assert t.check({}) is False
    assert t.check({"x": 1}) is False


def test_timer_fires_on_first_call():
    t = TimerTrigger(EventTriggerConfig(
        identifier="e", trigger_type=TriggerType.TIMER, interval_sec=60,
    ))
    # 第一次必触发(因为 cooldown_ok 默认间隔 60s)
    assert t.check({}) is True
    # 紧接着再 check -> cooldown 阻止
    assert t.check({}) is False


def test_threshold_above_edge_trigger():
    """从 5 -> 10 应该触发一次(边沿);持续 10 不重复触发"""
    t = ThresholdTrigger(EventTriggerConfig(
        identifier="e", trigger_type=TriggerType.THRESHOLD,
        watch_identifier="temp", op=">", threshold=8.0,
    ))
    # 5 不触发
    assert t.check({"temp": 5}) is False
    # 10 触发
    assert t.check({"temp": 10}) is True
    # 还是 10 不再触发
    assert t.check({"temp": 10}) is False
    # 回到 5(下降)
    assert t.check({"temp": 5}) is False
    # 又升到 10 再次触发
    assert t.check({"temp": 10}) is True


def test_threshold_ignore_missing():
    t = ThresholdTrigger(EventTriggerConfig(
        identifier="e", trigger_type=TriggerType.THRESHOLD,
        watch_identifier="temp", op=">", threshold=8.0,
    ))
    assert t.check({}) is False
    assert t.check({"other": 100}) is False
