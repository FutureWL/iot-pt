"""属性生成器 - 策略模式

4 种策略:
  - random:  区间随机
  - increment: 步进递增(可绕回)
  - const:  固定值
  - sine:   正弦波(模拟温度/压力)
"""
from __future__ import annotations

import math
import random
import time
from abc import ABC, abstractmethod
from typing import Any

from ..model.enums import GenStrategy, PropertyDataType
from .config import PropertyGenConfig


class GenContext:
    """生成器共享上下文"""
    def __init__(self) -> None:
        self.t0: float = time.time()

    def elapsed(self) -> float:
        return time.time() - self.t0


class BaseGen(ABC):
    """属性生成器基类"""
    def __init__(self, cfg: PropertyGenConfig, ctx: GenContext) -> None:
        self.cfg = cfg
        self.ctx = ctx
        self.current: Any = cfg.initial

    @abstractmethod
    def next(self) -> tuple[Any, str]:
        """生成下一个值,返回 (值, 展示文本)"""

    def type(self) -> PropertyDataType:
        t = self.cfg.type
        if t in ("int", "long"):
            return PropertyDataType.INT
        if t in ("double", "float"):
            return PropertyDataType.DOUBLE
        if t in ("boolean", "bool"):
            return PropertyDataType.BOOLEAN
        return PropertyDataType.TEXT


class RandomGen(BaseGen):
    """区间随机"""
    def next(self) -> tuple[Any, str]:
        t = self.type()
        lo = self.cfg.min if self.cfg.min is not None else 0
        hi = self.cfg.max if self.cfg.max is not None else 100
        if t == PropertyDataType.INT:
            v = random.randint(int(lo), int(hi))
            self.current = v
            return v, str(v)
        if t == PropertyDataType.DOUBLE:
            v = random.uniform(float(lo), float(hi))
            v = round(v, self.cfg.precision)
            self.current = v
            return v, f"{v:.{self.cfg.precision}f}"
        if t == PropertyDataType.BOOLEAN:
            v = random.random() > 0.5
            self.current = v
            return v, "ON" if v else "OFF"
        v = f"value-{random.randint(0, 9999)}"
        self.current = v
        return v, v


class IncrementGen(BaseGen):
    """步进递增(可绕回)"""
    def next(self) -> tuple[Any, str]:
        t = self.type()
        cur = self.current if self.current is not None else self.cfg.initial
        if cur is None:
            cur = self.cfg.min if self.cfg.min is not None else 0
        try:
            cur = float(cur)
        except (TypeError, ValueError):
            cur = 0
        v = cur + self.cfg.step
        if self.cfg.max is not None and v > self.cfg.max:
            v = self.cfg.min if self.cfg.min is not None else 0  # 绕回
        if t == PropertyDataType.INT:
            v = int(v)
        elif t == PropertyDataType.DOUBLE:
            v = round(v, self.cfg.precision)
        self.current = v
        return v, f"{v:.{self.cfg.precision}f}" if isinstance(v, float) else str(v)


class ConstGen(BaseGen):
    """固定值"""
    def next(self) -> tuple[Any, str]:
        v = self.current if self.current is not None else self.cfg.initial
        if v is None:
            v = "" if self.type() == PropertyDataType.TEXT else 0
        self.current = v
        return v, str(v)


class SineGen(BaseGen):
    """正弦波 - 模拟温度/压力等周期性数据"""
    def next(self) -> tuple[Any, str]:
        base = self.cfg.base if self.cfg.base is not None else (
            self.cfg.min if self.cfg.min is not None else 25
        )
        amp = self.cfg.amplitude if self.cfg.amplitude is not None else 5
        period = self.cfg.period_sec if self.cfg.period_sec > 0 else 60
        v = base + amp * math.sin(2 * math.pi * self.ctx.elapsed() / period)
        if self.type() == PropertyDataType.INT:
            v = int(v)
        else:
            v = round(v, self.cfg.precision)
        self.current = v
        return v, f"{v:.{self.cfg.precision}f}" if isinstance(v, float) else str(v)


def build_generator(cfg: PropertyGenConfig, ctx: GenContext) -> BaseGen:
    """工厂方法"""
    match cfg.strategy:
        case GenStrategy.RANDOM:
            return RandomGen(cfg, ctx)
        case GenStrategy.INCREMENT:
            return IncrementGen(cfg, ctx)
        case GenStrategy.CONST:
            return ConstGen(cfg, ctx)
        case GenStrategy.SINE:
            return SineGen(cfg, ctx)
        case _:
            raise ValueError(f"未知策略: {cfg.strategy}")


__all__ = [
    "GenContext",
    "BaseGen",
    "RandomGen",
    "IncrementGen",
    "ConstGen",
    "SineGen",
    "build_generator",
]
