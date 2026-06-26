#!/usr/bin/env python3
"""
模拟设备通过 MQTT 上报属性的测试脚本

用法:
  python3 scripts/test-mqtt-publish.py TH-001 25.6 58.2 85

第 1 个参数: deviceKey (TH-001, TH-002 ...)
第 2 个参数: 温度 (默认 25.6)
第 3 个参数: 湿度 (默认 58.2)
第 4 个参数: 电池电量 (默认 85)

需要在 backend 端有同 productKey 的设备 + 物模型
"""
import paho.mqtt.client as mqtt
import json
import sys
import time

BROKER = "localhost"
PORT = 33405
PRODUCT_KEY = "th_sensor_v1"  # 工业温湿度传感器


def main():
    if len(sys.argv) < 2:
        print("用法: python3 test-mqtt-publish.py <deviceKey> [温度] [湿度] [电量]")
        sys.exit(1)

    device_key = sys.argv[1]
    temp = float(sys.argv[2]) if len(sys.argv) > 2 else 25.6
    humi = float(sys.argv[3]) if len(sys.argv) > 3 else 58.2
    battery = int(sys.argv[4]) if len(sys.argv) > 4 else 85

    topic = f"iot/{PRODUCT_KEY}/{device_key}/property/post"
    payload = {
        "temperature": temp,
        "humidity": humi,
        "battery": battery
    }

    print(f"→ Broker:   {BROKER}:{PORT}")
    print(f"→ Topic:    {topic}")
    print(f"→ Payload:  {json.dumps(payload, ensure_ascii=False)}")

    client = mqtt.Client(client_id=f"sim-{device_key}-{int(time.time())}")
    try:
        client.connect(BROKER, PORT, 10)
        client.loop_start()
        result = client.publish(topic, json.dumps(payload), qos=1)
        result.wait_for_publish(timeout=5)
        print("✓ 已发布")
    except Exception as e:
        print(f"✗ 失败: {e}")
        sys.exit(1)
    finally:
        client.loop_stop()
        client.disconnect()


if __name__ == "__main__":
    main()