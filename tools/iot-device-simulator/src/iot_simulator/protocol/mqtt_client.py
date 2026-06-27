"""MQTT 客户端 - 基于 paho-mqtt

主题规范(与 iot-pt/backend/.../MqttProtocolAdapter.java 一致):
  上行属性: iot/{productKey}/{deviceKey}/property/post
  上行事件: iot/{productKey}/{deviceKey}/event/post
  下行属性: iot/{productKey}/{deviceKey}/property/set
  下行服务: iot/{productKey}/{deviceKey}/service/{id}/invoke

Payload 格式(JSON):
  属性: {"temperature": 25.6, "humidity": 58.2}
  事件: {"identifier": "high_temp", "value": {...}, "timestamp": 1700000000}
  服务回复: {"identifier": "reboot", "code": 0, "message": "ok", "data": {...}}
"""
from __future__ import annotations

import json
import time
import uuid
from typing import Callable, Optional

import paho.mqtt.client as mqtt
from loguru import logger

from ..core.config import MqttConfig
from .messages import RxMessage, TxMessage


Callback = Callable[[RxMessage], None]


class MqttClient:
    def __init__(self, cfg: MqttConfig, product_key: str, device_key: str) -> None:
        self.cfg = cfg
        self.product_key = product_key
        self.device_key = device_key
        self._client: Optional[mqtt.Client] = None
        self._on_message_cb: Optional[Callback] = None
        self._connected = False
        self._loop_started = False

    # ----- 主题构造 -----
    def _topic_prop_post(self) -> str:
        return f"iot/{self.product_key}/{self.device_key}/property/post"

    def _topic_event_post(self) -> str:
        return f"iot/{self.product_key}/{self.device_key}/event/post"

    def _topic_prop_set(self) -> str:
        return f"iot/{self.product_key}/{self.device_key}/property/set"

    def _topic_service_invoke(self, service_id: str) -> str:
        return f"iot/{self.product_key}/{self.device_key}/service/{service_id}/invoke"

    # ----- 生命周期 -----
    def connect(self, on_message: Callback) -> None:
        self._on_message_cb = on_message
        client_id = self.cfg.client_id or f"simulator-{self.device_key}"

        # paho-mqtt v2 API
        self._client = mqtt.Client(
            callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
            client_id=client_id,
            clean_session=True,
        )
        if self.cfg.username:
            self._client.username_pw_set(self.cfg.username, self.cfg.password)
        if self.cfg.use_tls:
            self._client.tls_set()  # 用默认 CA

        self._client.on_connect = self._on_connect
        self._client.on_disconnect = self._on_disconnect
        self._client.on_message = self._on_message

        logger.info("[MQTT] 连接 {}:{} client_id={}", self.cfg.host, self.cfg.port, client_id)
        self._client.connect(self.cfg.host, self.cfg.port, keepalive=self.cfg.keepalive)
        self._client.loop_start()
        self._loop_started = True

    def disconnect(self) -> None:
        if self._client and self._loop_started:
            try:
                self._client.loop_stop()
            except Exception:
                pass
            self._loop_started = False
        if self._client:
            try:
                self._client.disconnect()
            except Exception:
                pass
        self._connected = False

    @property
    def is_connected(self) -> bool:
        return self._connected

    # ----- 收发 -----
    def publish_property(self, data: dict) -> TxMessage:
        msg = TxMessage(topic=self._topic_prop_post(), payload=data, qos=self.cfg.qos)
        self._publish(msg)
        return msg

    def publish_event(self, identifier: str, value: dict) -> TxMessage:
        payload = {
            "identifier": identifier,
            "value": value,
            "timestamp": int(time.time() * 1000),
        }
        msg = TxMessage(topic=self._topic_event_post(), payload=payload, qos=self.cfg.qos)
        self._publish(msg)
        return msg

    def reply_service(self, service_id: str, code: int, message: str, data: dict | None = None) -> TxMessage:
        # 服务回复也发到 event 主题(简化;真实场景可能用专门的 reply 主题)
        payload = {
            "identifier": service_id,
            "code": code,
            "message": message,
            "data": data or {},
            "timestamp": int(time.time() * 1000),
        }
        msg = TxMessage(topic=self._topic_event_post(), payload=payload, qos=self.cfg.qos)
        self._publish(msg)
        return msg

    def _publish(self, msg: TxMessage) -> None:
        if not self._client or not self._connected:
            logger.warning("[MQTT] 未连接,丢弃 TX: {}", msg.topic)
            return
        try:
            body = json.dumps(msg.payload, ensure_ascii=False)
            info = self._client.publish(msg.topic, body, qos=msg.qos)
            if info.rc != mqtt.MQTT_ERR_SUCCESS:
                logger.warning("[MQTT] publish 失败: rc={}", info.rc)
        except Exception as e:
            logger.error("[MQTT] publish 异常: {}", e)

    # ----- 回调 -----
    def _on_connect(self, client, userdata, flags, reason_code, properties=None):
        if reason_code == 0:
            self._connected = True
            logger.info("[MQTT] 已连接")
            # 订阅下行主题
            self._client.subscribe(self._topic_prop_set(), qos=self.cfg.qos)
            # 订阅所有服务调用(通配)
            self._client.subscribe(
                f"iot/{self.product_key}/{self.device_key}/service/+/invoke",
                qos=self.cfg.qos,
            )
            logger.info(
                "[MQTT] 已订阅: {}, service/+/invoke",
                self._topic_prop_set(),
            )
        else:
            logger.error("[MQTT] 连接失败: rc={}", reason_code)

    def _on_disconnect(self, client, userdata, flags, reason_code, properties=None):
        self._connected = False
        logger.warning("[MQTT] 断开连接: rc={}", reason_code)

    def _on_message(self, client, userdata, msg):
        try:
            payload_str = msg.payload.decode("utf-8", errors="replace")
            try:
                payload = json.loads(payload_str)
            except json.JSONDecodeError:
                payload = {"_raw": payload_str}
            rx = RxMessage(topic=msg.topic, payload=payload, raw=payload_str)
            logger.debug("[MQTT] RX {}: {}", msg.topic, payload_str[:200])
            if self._on_message_cb:
                self._on_message_cb(rx)
        except Exception as e:
            logger.error("[MQTT] 消息处理异常: {}", e)


__all__ = ["MqttClient"]
