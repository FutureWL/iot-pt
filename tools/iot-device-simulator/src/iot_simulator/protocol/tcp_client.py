"""TCP 客户端 - 与后端 TcpProtocolAdapter 的帧协议对齐

帧协议(每行一个 JSON, \\n 结尾):
  1. auth:      {"type":"auth","productKey":"...","deviceKey":"...","secret":"..."}
  2. property:  {"type":"property","data":{...}}
  3. event:     {"type":"event","identifier":"...","value":{...},"timestamp":...}
  4. set_reply: {"type":"set_reply","identifier":"...","code":0,"message":"ok"}
  5. ping:      {"type":"ping"}  -> 回复 {"type":"pong"}

服务调用下行(平台→设备):
  主题约定: device.{productKey}.{deviceKey}.service.{serviceId}
  载荷: {"type":"service_invoke","identifier":"reboot","input":{...}}
"""
from __future__ import annotations

import json
import queue
import socket
import threading
import time
from typing import Callable, Optional

from loguru import logger

from ..core.config import TcpConfig
from .messages import RxMessage, TxMessage


Callback = Callable[[RxMessage], None]


class TcpClient:
    """阻塞式 TCP 客户端 - 独立线程读写"""

    def __init__(self, cfg: TcpConfig, product_key: str, device_key: str) -> None:
        self.cfg = cfg
        self.product_key = product_key
        self.device_key = device_key
        self._sock: Optional[socket.socket] = None
        self._reader: Optional[threading.Thread] = None
        self._writer: Optional[threading.Thread] = None
        self._on_message_cb: Optional[Callback] = None
        self._send_queue: queue.Queue[TxMessage] = queue.Queue()
        self._connected = False
        self._stop = threading.Event()

    @property
    def is_connected(self) -> bool:
        return self._connected

    def connect(self, on_message: Callback, auth_payload: dict) -> None:
        self._on_message_cb = on_message
        self._stop.clear()
        self._sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self._sock.settimeout(self.cfg.connect_timeout_sec)
        try:
            self._sock.connect((self.cfg.host, self.cfg.port))
        except (socket.timeout, OSError) as e:
            logger.error("[TCP] 连接失败: {}", e)
            self._sock = None
            raise

        # 启动读写线程
        self._reader = threading.Thread(
            target=self._read_loop, name="tcp-reader", daemon=True
        )
        self._writer = threading.Thread(
            target=self._write_loop, name="tcp-writer", daemon=True
        )
        self._reader.start()
        self._writer.start()
        self._connected = True
        logger.info("[TCP] 已连接 {}:{}", self.cfg.host, self.cfg.port)

        # 发送 auth 帧
        self._send_queue.put(TxMessage(topic="", payload=auth_payload, qos=0))

    def disconnect(self) -> None:
        self._stop.set()
        self._connected = False
        if self._sock:
            try:
                self._sock.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass
            try:
                self._sock.close()
            except OSError:
                pass
            self._sock = None
        logger.info("[TCP] 已断开")

    # ----- 业务方法 -----
    def publish_property(self, data: dict) -> TxMessage:
        msg = TxMessage(
            topic="property",
            payload={"type": "property", "data": data},
            qos=0,
        )
        self._send_queue.put(msg)
        return msg

    def publish_event(self, identifier: str, value: dict) -> TxMessage:
        msg = TxMessage(
            topic="event",
            payload={
                "type": "event",
                "identifier": identifier,
                "value": value,
                "timestamp": int(time.time() * 1000),
            },
            qos=0,
        )
        self._send_queue.put(msg)
        return msg

    def reply_service(self, service_id: str, code: int, message: str, data: dict | None = None) -> TxMessage:
        msg = TxMessage(
            topic="service_reply",
            payload={
                "type": "service_reply",
                "identifier": service_id,
                "code": code,
                "message": message,
                "data": data or {},
            },
            qos=0,
        )
        self._send_queue.put(msg)
        return msg

    def reply_property_set(self, identifier: str, code: int = 0, message: str = "ok") -> TxMessage:
        """回复属性设置"""
        msg = TxMessage(
            topic="set_reply",
            payload={
                "type": "set_reply",
                "identifier": identifier,
                "code": code,
                "message": message,
            },
            qos=0,
        )
        self._send_queue.put(msg)
        return msg

    # ----- 内部线程 -----
    def _write_loop(self) -> None:
        """发送线程 - 从队列取帧并发送"""
        while not self._stop.is_set() and self._sock:
            try:
                msg = self._send_queue.get(timeout=0.5)
            except queue.Empty:
                continue
            if not self._sock:
                break
            try:
                line = json.dumps(msg.payload, ensure_ascii=False) + "\n"
                self._sock.sendall(line.encode("utf-8"))
            except OSError as e:
                logger.error("[TCP] 发送失败: {}", e)
                self._connected = False
                break

    def _read_loop(self) -> None:
        """接收线程 - 一行行解析"""
        buf = b""
        fileobj = None
        try:
            if self._sock is None:
                return
            fileobj = self._sock.makefile("r", encoding="utf-8", newline="\n")
            while not self._stop.is_set():
                line = fileobj.readline()
                if not line:
                    logger.warning("[TCP] 对端关闭")
                    break
                line = line.rstrip("\n").rstrip("\r")
                if not line:
                    continue
                try:
                    payload = json.loads(line)
                except json.JSONDecodeError as e:
                    logger.warning("[TCP] JSON 解析失败: {} | line={}", e, line[:200])
                    continue
                # 解析 type,转 RxMessage
                msg_type = payload.get("type", "")
                if msg_type == "service_invoke":
                    # 服务调用下行
                    topic = f"device.{self.product_key}.{self.device_key}.service.{payload.get('identifier', '')}"
                    rx = RxMessage(topic=topic, payload=payload, raw=line)
                elif msg_type == "property_set":
                    topic = f"device.{self.product_key}.{self.device_key}.property.set"
                    rx = RxMessage(topic=topic, payload=payload, raw=line)
                elif msg_type == "pong":
                    logger.debug("[TCP] 收到 pong")
                    continue
                elif msg_type in ("auth_ack", "auth_ok"):
                    logger.info("[TCP] 鉴权通过")
                    continue
                elif msg_type == "err":
                    logger.warning("[TCP] 错误: {}", payload.get("message"))
                    continue
                else:
                    topic = f"device.{self.product_key}.{self.device_key}.{msg_type}"
                    rx = RxMessage(topic=topic, payload=payload, raw=line)
                if self._on_message_cb:
                    self._on_message_cb(rx)
        except OSError as e:
            if not self._stop.is_set():
                logger.error("[TCP] 接收异常: {}", e)
        finally:
            self._connected = False
            if fileobj:
                try:
                    fileobj.close()
                except Exception:
                    pass


__all__ = ["TcpClient"]
