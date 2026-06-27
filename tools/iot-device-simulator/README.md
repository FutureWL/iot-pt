# IoT 设备模拟器 (IoT Device Simulator)

独立的 GUI 程序，模拟 IoT 设备向平台上报数据，用于：

- 设备工程师调试物模型
- 现场演示
- 自动化测试
- 没有真实设备时验证规则引擎

## 特性

- **双协议支持**：MQTT（基于 Eclipse Paho）+ TCP（自定义 JSON 行协议）
- **物模型驱动**：手填物模型后自动生成属性 UI
- **4 种属性生成策略**：随机 / 递增 / 固定 / 正弦波
- **事件触发**：手动 + 阈值 + 定时
- **服务调用响应**：自动 / 手动
- **实时日志**：TX/RX 全量记录
- **跨平台**：Linux 优先（Windows/macOS 后续）
- **跟随系统主题**：自动适配浅色/深色

## 与平台协议兼容

MQTT 主题规范（与 `iot-pt/backend/.../MqttProtocolAdapter.java` 一致）：

```
上行(设备→平台):
  iot/{productKey}/{deviceKey}/property/post     # 属性上报
  iot/{productKey}/{deviceKey}/event/post        # 事件上报

下行(平台→设备):
  iot/{productKey}/{deviceKey}/property/set      # 属性设置
  iot/{productKey}/{deviceKey}/service/{id}/invoke  # 服务调用
```

TCP 帧协议（每行一个 JSON，以 `\n` 结尾）：

```json
{"type":"auth","productKey":"pk","deviceKey":"dk","secret":"..."}
{"type":"property","data":{"temperature":25.6}}
{"type":"event","identifier":"fault","value":{...}}
```

## 快速开始

### 安装（开发模式）

```bash
cd tools/iot-device-simulator
python3 -m venv .venv
source .venv/bin/activate
pip install -e ".[dev]"
iot-simulator
```

### 直接运行

```bash
python -m iot_simulator
```

### 打包

```bash
./scripts/build.sh
# 产物: dist/iot-device-simulator
```

## 目录结构

```
src/iot_simulator/
├── core/               # 业务逻辑（与 UI 解耦）
│   ├── simulator.py    # 模拟器主类
│   ├── config.py        # Pydantic 配置
│   ├── property_gen.py  # 属性生成器（策略模式）
│   ├── event_trigger.py # 事件触发器
│   └── service_handler.py
├── protocol/           # 协议层
│   ├── mqtt_client.py
│   ├── tcp_client.py
│   └── messages.py
├── model/              # 数据模型
│   └── thing_model.py
├── ui/                 # PySide6
│   ├── main_window.py
│   └── widgets/
└── utils/              # 工具
    ├── logger.py
    └── storage.py
```

## 路线图

- [x] v0.1: 项目骨架 + MQTT + 基础 UI
- [ ] v0.2: TCP 协议 + 物模型导入
- [ ] v0.3: 打包成单可执行文件
- [ ] v0.4: 物模型 HTTP API 拉取
- [ ] v0.5: 录制/回放
- [ ] v1.0: 多设备 Tab + 主题切换
