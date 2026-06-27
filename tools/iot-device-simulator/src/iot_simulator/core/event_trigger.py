"""事件触发器 - 决定何时触发事件

3 种触发方式:
  - manual:   UI 按钮(由调用方显式 fire)
  - timer:    每 N 秒触发一次
  - threshold: 监听某个属性的值,达到阈值时触发
"""
from __future__ import annotations

import time
from abc import ABC, abstractmethod
from typing import Any, Callable

from ..model.enums import TriggerType
from .config import EventTriggerConfig


class BaseTrigger(ABC):
    def __init__(self, cfg: EventTriggerConfig) -> None:
        self.cfg = cfg
        self.last_fire_ts: float = 0.0

    @abstractmethod
    def check(self, data: dict[str, Any]) -> bool:
        """返回是否应该触发"""

    def cooldown_ok(self, min_gap: float = 1.0) -> bool:
        """简单的去抖动: 触发后 1s 内不重复"""
        return time.time() - self.last_fire_ts >= min_gap

    def mark_fired(self) -> None:
        self.last_fire_ts = time.time()


class ManualTrigger(BaseTrigger):
    """手动触发 - check 永远 False,只通过显式 fire() 触发"""
    def check(self, data: dict[str, Any]) -> bool:
        return False


class TimerTrigger(BaseTrigger):
    """定时触发 - 每 N 秒触发一次"""
    def check(self, data: dict[str, Any]) -> bool:
        if self.cooldown_ok(self.cfg.interval_sec):
            self.mark_fired()
            return True
        return False


class ThresholdTrigger(BaseTrigger):
    """阈值触发 - 属性达到阈值时触发"""
    def __init__(self, cfg: EventTriggerConfig) -> None:
        super().__init__(cfg)
        self._triggered: bool = False  # 边沿检测: 只在跨越阈值时触发

    def check(self, data: dict[str, Any]) -> bool:
        if not self.cfg.watch_identifier:
            return False
        v = data.get(self.cfg.watch_identifier)
        if v is None:
            return False
        try:
            v = float(v)
        except (TypeError, ValueError):
            return False

        threshold = self.cfg.threshold
        match self.cfg.op:
            case ">":
                now_above = v > threshold
            case "<":
                now_above = v < threshold
            case ">=":
                now_above = v >= threshold
            case "<=":
                now_above = v <= threshold
            case "==":
                now_above = v == threshold
            case "!=":
                now_above = v != threshold
            case _:
                now_above = False

        # 边沿检测: 从未触发到触发才返回 True(避免一直高过阈值时持续触发)
        if now_above and not self._triggered:
            self._triggered = True
            return True
        if not now_above:
            self._triggered = False
        return False


def build_trigger(cfg: EventTriggerConfig) -> BaseTrigger:
    match cfg.trigger_type:
        case TriggerType.MANUAL:
            return ManualTrigger(cfg)
        case TriggerType.TIMER:
            return TimerTrigger(cfg)
        case TriggerType.THRESHOLD:
            return ThresholdTrigger(cfg)
        case _:
            raise ValueError(f"未知触发器: {cfg.trigger_type}")


__all__ = [
    "BaseTrigger",
    "ManualTrigger",
    "TimerTrigger",
    "ThresholdTrigger",
    "build_trigger",
]
