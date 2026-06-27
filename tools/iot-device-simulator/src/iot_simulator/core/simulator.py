"""DeviceSimulator - 单台设备的模拟核心

职责:
  1. 维护连接(MQTT/TCP)
  2. 周期生成属性 + 上报
  3. 检查事件触发条件 + 上报事件
  4. 处理下行(属性设置 / 服务调用)
  5. 输出 LogEntry 给 UI

生命周期:
  configure(cfg) -> start() -> 循环跑 -> stop()
"""
from __future__ import annotations

import asyncio
import time
from typing import Optional

from loguru import logger
from PySide6.QtCore import QObject, Signal

from ..model.thing_model import ThingModel
from ..protocol.messages import ConnState, LogEntry, RxMessage
from ..protocol.mqtt_client import MqttClient
from ..protocol.tcp_client import TcpClient
from .config import DeviceConfig
from .event_trigger import build_trigger
from .property_gen import GenContext, build_generator


class DeviceSimulator(QObject):
    """单设备模拟器 - QObject,可在主线程直接使用

    不开独立线程,所有循环在主线程的 QTimer 驱动下运行(避免线程同步)。
    """

    # ---- 发给 UI 的信号 ----
    state_changed = Signal(object)            # ConnState
    log_emitted = Signal(object)              # LogEntry
    property_changed = Signal(str, object)    # identifier, value
    stats_changed = Signal(dict)              # 上报/事件/错误统计

    def __init__(self, parent: Optional[QObject] = None) -> None:
        super().__init__(parent)
        self.cfg: Optional[DeviceConfig] = None
        self.thing_model: ThingModel = ThingModel()
        self.connection = None
        self._gen_ctx = GenContext()
        self._generators: dict[str, object] = {}     # identifier -> BaseGen
        self._triggers: list = []                    # BaseTrigger list
        self._last_reported: dict[str, object] = {}  # 上次上报值(用于只报变化)
        self._last_tick: float = 0.0
        self._state = ConnState()
        self._stats = {
            "tx_property": 0,
            "tx_event": 0,
            "rx_downlink": 0,
            "parse_error": 0,
        }
        # 待执行的下一次触发(边沿触发器记录)
        self._triggered_flags: dict[str, bool] = {}

    # ============================================================
    # 生命周期
    # ============================================================
    def configure(self, cfg: DeviceConfig) -> None:
        """应用配置(不连接)"""
        self.cfg = cfg
        try:
            self.thing_model = ThingModel.from_json(cfg.thing_model_json)
        except ValueError as e:
            logger.warning("物模型解析失败,使用空模型: {}", e)
            self.thing_model = ThingModel()
        self._generators = {
            p.identifier: build_generator(p, self._gen_ctx)
            for p in cfg.properties
        }
        self._triggers = [build_trigger(e) for e in cfg.events]
        self._last_reported = {}
        self._triggered_flags = {e.identifier: False for e in cfg.events}
        logger.info("已加载配置: {} 个属性 / {} 个事件 / {} 个服务",
                    len(cfg.properties), len(cfg.events), len(cfg.services))

    def start(self) -> bool:
        """启动模拟 - 建立连接后立即开始循环"""
        if not self.cfg:
            self._log("ERROR", "未配置")
            return False
        if self._state.connected:
            logger.info("已在运行中")
            return True

        cfg = self.cfg
        if cfg.protocol.value == "MQTT":
            self.connection = MqttClient(cfg.mqtt, cfg.product_key, cfg.device_key)
        else:
            self.connection = TcpClient(cfg.tcp, cfg.product_key, cfg.device_key)

        # 设置连接状态
        self._state.protocol = cfg.protocol.value
        self._state.host = cfg.mqtt.host if cfg.protocol.value == "MQTT" else cfg.tcp.host
        self._state.port = cfg.mqtt.port if cfg.protocol.value == "MQTT" else cfg.tcp.port
        self._state.error = ""
        self._emit_state()

        try:
            if cfg.protocol.value == "MQTT":
                self.connection.connect(self._on_message)
            else:
                # TCP 先建立 socket 连接(同步),失败时 connection=None
                auth_payload = {
                    "type": "auth",
                    "productKey": cfg.product_key,
                    "deviceKey": cfg.device_key,
                    "secret": cfg.device_secret,
                }
                # TcpClient.connect 内部异步启动线程
                # 需要循环等连接建立(最简单是轮询 is_connected)
                self.connection.connect(self._on_message, auth_payload)
        except Exception as e:
            self._state.connected = False
            self._state.error = str(e)
            self._emit_state()
            self._log("ERROR", f"连接失败: {e}")
            return False

        self._state.connected = True
        self._emit_state()
        self._log("INFO", f"已连接 {self._state.protocol} {self._state.host}:{self._state.port}")
        return True

    def stop(self) -> None:
        """停止模拟"""
        if self.connection:
            try:
                self.connection.disconnect()
            except Exception as e:
                logger.warning("disconnect 异常: {}", e)
            self.connection = None
        self._state.connected = False
        self._emit_state()
        self._log("INFO", "已停止")

    def fire_event_manual(self, identifier: str) -> bool:
        """手动触发某个事件(从 UI 调用)"""
        if not self.connection or not self._state.connected:
            return False
        ev_cfg = next((e for e in (self.cfg.events if self.cfg else []) if e.identifier == identifier), None)
        if not ev_cfg:
            self._log("ERROR", f"事件 {identifier} 不存在")
            return False
        return self._fire_event(ev_cfg)

    # ============================================================
    # 主循环(由 QTimer 驱动,每 100ms 调用一次)
    # ============================================================
    def tick(self) -> None:
        """主循环一次迭代 - 由 QTimer 周期触发"""
        if not self._state.connected or not self.connection:
            return
        now = time.time()
        if now - self._last_tick < 0.05:  # 限频: 20Hz
            return
        self._last_tick = now

        # 1. 检查每个属性的上报周期
        data: dict[str, object] = {}
        for ident, gen in self._generators.items():
            # 这里简化: 不做每属性独立周期,统一在 tick 里轮询
            value, _ = gen.next()
            data[ident] = value

        # 2. 变化才上报
        if self._should_report(data):
            self._report_property(data)
            self._last_reported = data.copy()

        # 3. 检查事件触发
        for trig in self._triggers:
            if trig.check(data):
                # 找对应的 cfg
                cfg_ev = next(
                    (e for e in (self.cfg.events if self.cfg else []) if e.identifier == trig.cfg.identifier),
                    None,
                )
                if cfg_ev:
                    self._fire_event(cfg_ev)

    # ============================================================
    # 内部方法
    # ============================================================
    def _should_report(self, data: dict[str, object]) -> bool:
        if not self.cfg or not self.cfg.report_on_change_only:
            return True
        if not self._last_reported:
            return True
        for k, v in data.items():
            if self._last_reported.get(k) != v:
                return True
        return False

    def _report_property(self, data: dict[str, object]) -> None:
        if not self.connection:
            return
        try:
            tx = self.connection.publish_property(data)
            self._stats["tx_property"] += 1
            self._log_tx("TX", tx.topic, tx.payload)
            for k, v in data.items():
                self.property_changed.emit(k, v)
            self.stats_changed.emit(self._stats.copy())
        except Exception as e:
            self._stats["parse_error"] += 1
            self._log("ERROR", f"属性上报失败: {e}")

    def _fire_event(self, ev_cfg) -> bool:
        if not self.connection:
            return False
        try:
            tx = self.connection.publish_event(ev_cfg.identifier, ev_cfg.output)
            self._stats["tx_event"] += 1
            self._log_tx("TX", tx.topic, tx.payload)
            self.stats_changed.emit(self._stats.copy())
            return True
        except Exception as e:
            self._stats["parse_error"] += 1
            self._log("ERROR", f"事件上报失败: {e}")
            return False

    def _on_message(self, rx: RxMessage) -> None:
        """收到下行消息(在协议线程/回调中)"""
        self._stats["rx_downlink"] += 1
        self._log_rx("RX", rx.topic, rx.payload)
        # 解析 service_invoke / property_set
        payload = rx.payload or {}
        msg_type = payload.get("type", "")
        if msg_type == "service_invoke" or "/service/" in rx.topic:
            self._handle_service_invoke(rx)
        elif msg_type == "property_set" or rx.topic.endswith("/property/set"):
            self._handle_property_set(rx)
        self.stats_changed.emit(self._stats.copy())

    def _handle_service_invoke(self, rx: RxMessage) -> None:
        ident = rx.payload.get("identifier", "")
        # 查 cfg.services
        svc = next(
            (s for s in (self.cfg.services if self.cfg else []) if s.identifier == ident),
            None,
        )
        if not svc:
            self._log("WARN", f"收到未知服务调用: {ident}")
            return
        self._log("INFO", f"收到服务调用: {ident} (auto_reply={svc.auto_reply})")
        if not svc.auto_reply:
            return  # 等用户手动回复
        if svc.reply_delay_ms > 0:
            # 简化: 不开定时器,直接延迟(后续用 QTimer)
            time.sleep(svc.reply_delay_ms / 1000.0)
        try:
            if self.connection:
                if self.cfg and self.cfg.protocol.value == "MQTT":
                    self.connection.reply_service(ident, svc.reply_code, svc.reply_message, svc.reply_data)
                else:
                    self.connection.reply_service(ident, svc.reply_code, svc.reply_message, svc.reply_data)
                self._log("INFO", f"已回复服务: {ident}")
        except Exception as e:
            self._log("ERROR", f"服务回复失败: {e}")

    def _handle_property_set(self, rx: RxMessage) -> None:
        # 简化: 仅记录
        self._log("INFO", f"收到属性设置: {rx.payload.get('data') or rx.payload}")

    # ============================================================
    # 日志辅助
    # ============================================================
    def _log(self, level: str, msg: str) -> None:
        entry = LogEntry(ts=time.time(), direction=level, topic="", payload=msg, level=level)
        self.log_emitted.emit(entry)
        logger.log(level.upper(), msg)

    def _log_tx(self, direction: str, topic: str, payload: dict) -> None:
        entry = LogEntry(
            ts=time.time(),
            direction=direction,
            topic=topic,
            payload=self._format_payload(payload),
        )
        self.log_emitted.emit(entry)

    def _log_rx(self, direction: str, topic: str, payload: dict) -> None:
        entry = LogEntry(
            ts=time.time(),
            direction=direction,
            topic=topic,
            payload=self._format_payload(payload),
        )
        self.log_emitted.emit(entry)

    def _format_payload(self, payload: dict) -> str:
        import json
        try:
            return json.dumps(payload, ensure_ascii=False)
        except (TypeError, ValueError):
            return str(payload)

    def _emit_state(self) -> None:
        self.state_changed.emit(self._state)


__all__ = ["DeviceSimulator"]
