"""物模型测试"""
from __future__ import annotations

import pytest
from pydantic import ValidationError

from iot_simulator.model.thing_model import ThingModel


def test_empty_model():
    tm = ThingModel()
    assert tm.properties == []
    assert tm.events == []
    assert tm.services == []


def test_from_json_valid():
    raw = """{
        "version": "1.0",
        "properties": [
            {"identifier": "temp", "name": "温度", "accessMode": "RO", "dataType": {"type": "double"}}
        ],
        "events": [{"identifier": "alarm", "name": "告警", "type": "ERROR"}],
        "services": [{"identifier": "reboot", "name": "重启", "callType": "ASYNC"}]
    }"""
    tm = ThingModel.from_json(raw)
    assert len(tm.properties) == 1
    assert tm.properties[0].identifier == "temp"
    assert len(tm.events) == 1
    assert len(tm.services) == 1


def test_from_json_invalid_identifier():
    raw = '{"properties": [{"identifier": "123-bad!", "name": "X", "dataType": {"type": "int"}}]}'
    with pytest.raises(ValueError):
        ThingModel.from_json(raw)


def test_identifier_type_map():
    raw = """{
        "properties": [
            {"identifier": "a", "name": "a", "dataType": {"type": "int"}},
            {"identifier": "b", "name": "b", "dataType": {"type": "long"}},
            {"identifier": "c", "name": "c", "dataType": {"type": "double"}},
            {"identifier": "d", "name": "d", "dataType": {"type": "boolean"}},
            {"identifier": "e", "name": "e", "dataType": {"type": "text"}},
            {"identifier": "f", "name": "f", "dataType": {"type": "struct"}}
        ]
    }"""
    tm = ThingModel.from_json(raw)
    m = tm.identifier_type_map()
    from iot_simulator.model.enums import PropertyDataType
    assert m["a"] == PropertyDataType.INT
    assert m["b"] == PropertyDataType.INT  # long -> INT
    assert m["c"] == PropertyDataType.DOUBLE
    assert m["d"] == PropertyDataType.BOOLEAN
    assert m["e"] == PropertyDataType.TEXT
    assert m["f"] == PropertyDataType.TEXT  # struct -> TEXT
