"""属性生成器测试"""
from __future__ import annotations

import time

import pytest

from iot_simulator.core.config import PropertyGenConfig
from iot_simulator.core.property_gen import (
    GenContext,
    IncrementGen,
    RandomGen,
    SineGen,
)
from iot_simulator.model.enums import GenStrategy


@pytest.fixture
def ctx() -> GenContext:
    return GenContext()


def test_random_int_in_range(ctx):
    g = RandomGen(
        PropertyGenConfig(identifier="x", type="int", strategy=GenStrategy.RANDOM, min=10, max=20),
        ctx,
    )
    for _ in range(100):
        v, _ = g.next()
        assert 10 <= v <= 20
        assert isinstance(v, int)


def test_random_float_in_range(ctx):
    g = RandomGen(
        PropertyGenConfig(identifier="x", type="double", strategy=GenStrategy.RANDOM, min=0, max=1, precision=3),
        ctx,
    )
    for _ in range(50):
        v, text = g.next()
        assert 0 <= v <= 1
        # 精度检查
        assert len(text.split(".")[-1]) <= 3


def test_increment_wraps(ctx):
    g = IncrementGen(
        PropertyGenConfig(
            identifier="x", type="int", strategy=GenStrategy.INCREMENT,
            initial=0, step=5, max=10, min=0,
        ),
        ctx,
    )
    values = [g.next()[0] for _ in range(5)]
    assert values == [5, 10, 0, 5, 10]  # 10 之后绕回 0


def test_sine_oscillates(ctx):
    g = SineGen(
        PropertyGenConfig(
            identifier="x", type="double", strategy=GenStrategy.SINE,
            base=0, amplitude=10, period_sec=1.0, precision=2,
        ),
        ctx,
    )
    values = []
    # 短时间内采样
    for _ in range(20):
        v, _ = g.next()
        values.append(v)
        time.sleep(0.05)
    # 至少应该有变化
    assert len(set(values)) > 1
    # 大部分值应在 -10..10 之间
    for v in values:
        assert -10.1 <= v <= 10.1
