#!/usr/bin/env python3
"""
TCP 设备模拟器 - 模拟设备通过 JSON-over-TCP 协议上报数据

用法:
  python3 scripts/test-tcp-publish.py <deviceKey> [温度] [湿度] [电量]
  python3 scripts/test-tcp-publish.py --down  # 测下行:平台设置属性,设备响应

需要:
  - 后端 TCP 适配器已启动在 33410
  - 设备已存在 DB(productKey=th_sensor_v1,deviceKey=TH-xxx,secret 已知)
"""
import socket
import json
import sys
import time
import threading

HOST = "localhost"
PORT = 33410
PRODUCT_KEY = "th_sensor_v1"


def recv_line(sock: socket.socket) -> dict:
    """读取一行 JSON"""
    buf = b""
    while True:
        c = sock.recv(1)
        if not c or c == b"\n":
            break
        buf += c
    if not buf:
        return {}
    return json.loads(buf.decode("utf-8"))


def send_line(sock: socket.socket, obj: dict):
    line = (json.dumps(obj, ensure_ascii=False) + "\n").encode("utf-8")
    sock.sendall(line)


def auth_and_report(device_key: str, secret: str, points: int = 5, interval: float = 0.5):
    """认证 + 连续上报 + 收 ACK"""
    print(f"→ 连接到 {HOST}:{PORT}")
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.settimeout(5)
    s.connect((HOST, PORT))

    # 1) 认证
    send_line(s, {"type": "auth", "productKey": PRODUCT_KEY,
                  "deviceKey": device_key, "secret": secret})
    resp = recv_line(s)
    print(f"← AUTH 响应: {resp}")
    if not resp.get("ok"):
        print("认证失败,退出")
        s.close()
        return

    # 2) 连续上报
    for i in range(points):
        ts = int(time.time() * 1000)
        # 模拟数据
        t = 25 + (i % 5) * 1.5
        h = 50 + (i % 7) * 2.0
        b = max(5, 80 - i)
        send_line(s, {
            "type": "property",
            "ts": ts,
            "data": {
                "temperature": round(t, 2),
                "humidity": round(h, 2),
                "battery": b
            }
        })
        ack = recv_line(s)
        print(f"  上报 #{i+1}: t={t} h={h} b={b}  →  ACK: {ack}")
        time.sleep(interval)

    # 3) 上报一个事件
    send_line(s, {"type": "event", "name": "high_temp", "value": {"value": 38.5}})
    ack = recv_line(s)
    print(f"  事件上报: →  ACK: {ack}")

    print(f"✓ 完成 {points} 个数据点 + 1 个事件上报")
    time.sleep(0.5)
    s.close()


def down_test(device_key: str, secret: str):
    """测下行:平台设置属性,设备回 set_reply"""
    print(f"→ 连接到 {HOST}:{PORT} (下行测试)")
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.settimeout(5)
    s.connect((HOST, PORT))
    send_line(s, {"type": "auth", "productKey": PRODUCT_KEY,
                  "deviceKey": device_key, "secret": secret})
    resp = recv_line(s)
    print(f"← AUTH 响应: {resp}")
    if not resp.get("ok"):
        s.close()
        return

    # 后台线程:接收下行 set + 回复 set_reply
    def serve_down():
        while True:
            try:
                line = recv_line(s)
                if not line:
                    break
                if line.get("type") == "set":
                    identifier = line.get("identifier")
                    value = line.get("value")
                    print(f"  ← 平台下行 set: {identifier}={value}")
                    send_line(s, {
                        "type": "set_reply",
                        "identifier": identifier,
                        "success": True
                    })
            except Exception:
                break

    t = threading.Thread(target=serve_down, daemon=True)
    t.start()
    print("  设备侧已就绪,等待平台下行...")

    # 主动触发下行(通过后端 API 比较复杂,这里演示建立连接后)
    # 让用户手动去前端/接口触发下行
    print("  现在通过前端或 API 触发一次属性下发,这里会收到并回包")
    try:
        time.sleep(60)
    except KeyboardInterrupt:
        pass
    s.close()


def main():
    if len(sys.argv) < 2:
        print("用法:")
        print("  python3 test-tcp-publish.py <deviceKey> [点数]")
        print("  python3 test-tcp-publish.py <deviceKey> --down")
        sys.exit(1)

    device_key = sys.argv[1] if "--down" not in sys.argv else sys.argv[1]
    if len(sys.argv) > 2 and sys.argv[2] == "--down":
        # 需要设备 secret
        # 简化:从 API 拿
        import urllib.request
    import urllib.request
    # 登录
    req = urllib.request.Request(
        "http://localhost:33412/api/auth/login",
        data=json.dumps({"tenantCode": "default", "username": "admin", "password": "123456"}).encode(),
        headers={"Content-Type": "application/json"})
    token = json.loads(urllib.request.urlopen(req).read())["data"]["token"]

    # 用 /full 拿完整 secret(列表是脱敏的)
    page_req = urllib.request.Request(
        "http://localhost:33412/api/iot/device/page?pageSize=100",
        headers={"Authorization": f"Bearer {token}"})
    page = json.loads(urllib.request.urlopen(page_req).read())["data"]
    target = next((d for d in page["records"] if d["deviceKey"] == device_key), None)
    if not target:
        print(f"未找到设备 {device_key}"); sys.exit(1)
    full_req = urllib.request.Request(
        f"http://localhost:33412/api/iot/device/{target['id']}/full",
        headers={"Authorization": f"Bearer {token}"})
    secret = json.loads(urllib.request.urlopen(full_req).read())["data"]["deviceSecret"]
    print(f"→ 设备 {device_key} 完整 secret: {secret}")

    if len(sys.argv) > 2 and sys.argv[2] == "--down":
        down_test(device_key, secret)
    else:
        points = int(sys.argv[2]) if len(sys.argv) > 2 else 5
        auth_and_report(device_key, secret, points=points)


if __name__ == "__main__":
    main()