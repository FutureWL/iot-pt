# 快速启动

## 0. 环境准备

- **Docker** 20.10+
- **Docker Compose** v2+

> 操作系统支持 Linux / macOS / Windows (WSL2 推荐)

## 1. 一键启动

```bash
# 进入部署目录
cd deploy

# 复制环境变量(可选)
cp .env.example .env

# 启动所有服务(首次启动会拉镜像、构建后端和前端,约 5-10 分钟)
docker compose up -d

# 查看启动日志
docker compose logs -f backend
# 看到 ":: 物联网平台启动成功 ::" 即启动完成
```

## 2. 访问

| 服务 | 地址 | 默认账号 |
|---|---|---|
| **前端** | http://localhost:33400 | admin / 123456 |
| **后端 API** | http://localhost:33401/api | - |
| **Swagger** | http://localhost:33401/api/swagger-ui.html | - |
| **EMQX Dashboard** | http://localhost:33409 | admin / public |
| **MySQL** | localhost:33402 | iot / iot123456 |
| **TDengine REST** | localhost:33403 | root / taosdata |
| **MQTT** | localhost:33405 | - |
| **TCP Server** | localhost:33410 | - |

## 3. 登录测试

1. 打开 http://localhost:33400
2. 输入 `default` / `admin` / `123456`
3. 进入工作台

## 4. 查看日志

```bash
# 实时查看某个服务
docker compose logs -f backend
docker compose logs -f frontend

# 全部
docker compose logs -f

# 进入容器
docker compose exec backend sh
docker compose exec mysql mysql -uroot -proot123456 iot_platform
```

## 5. 关闭 / 重启

```bash
# 停止(保留数据)
docker compose down

# 停止 + 删数据
docker compose down -v

# 重启单个服务
docker compose restart backend

# 重新构建镜像(代码改动后)
docker compose build backend frontend
docker compose up -d
```

## 6. 本地开发模式

后端和前端可以单独跑,不影响其他容器:

### 6.1 后端开发

```bash
# 先停掉容器里的后端
docker compose stop backend

# 本地启动(需要 JDK 17 + Maven 3.9+)
cd backend
mvn spring-boot:run \
    -Dspring-boot.run.jvmArguments="\
        -DMYSQL_HOST=localhost -DMYSQL_PORT=33402 \
        -DTDENGINE_HOST=localhost -DTDENGINE_PORT=33403 \
        -DMQTT_BROKER=tcp://localhost:33405 \
        -DTCP_PORT=33410"
```

### 6.2 前端开发

```bash
# 停掉容器里的前端
docker compose stop frontend

# 本地启动(需要 Node 20+)
cd frontend
npm install
npm run dev
# 访问 http://localhost:33400
```

## 7. 模拟设备测试(MQTT)

安装 Python paho-mqtt:

```bash
pip install paho-mqtt
```

运行 `scripts/mqtt_simulator.py`(见 `scripts/` 目录):

```python
import paho.mqtt.client as mqtt
import json, time, random

client = mqtt.Client(client_id="sim-temp-001")
client.username_pw_set("sim-temp-001", "123456")  # deviceKey / deviceSecret
client.connect("localhost", 33405, 60)

while True:
    payload = {
        "id": str(int(time.time() * 1000)),
        "params": {
            "temperature": round(20 + random.random() * 20, 2),
            "humidity": round(40 + random.random() * 30, 2)
        },
        "method": "thing.event.property.post"
    }
    topic = "/sys/your_product_key/sim-temp-001/thing/event/property/post"
    client.publish(topic, json.dumps(payload))
    time.sleep(5)
```

> M3 阶段协议层实现完成后,真实设备即可接入。当前脚本会收到消息但 `deviceKey` 校验需要先在平台创建设备。

## 8. 常见问题

### Q1: MySQL 容器重启后密码错误
**A**: 第一次启动的 init.sql 设定了 `iot/iot123456`。如果改了 env 变量需要删除 volume:
```bash
docker compose down -v
docker compose up -d
```
另外注意新端口: MySQL 主机端口是 33402,不是 3306。

### Q2: EMQX 启动慢
**A**: 第一次启动需要初始化数据库,约 30s。可用 `docker compose ps` 查看状态。

### Q3: TDengine 客户端连接失败
**A**: 检查 `tdengine` 容器是否健康。RESTful 主机端口 33403(容器内 6041),native 主机端口 33404(容器内 6030)。

### Q4: 前端调用后端 401
**A**: 检查 `frontend/nginx/conf.d/default.conf` 中 `proxy_pass http://backend:9000` 是否正确(容器内部仍用 9000,主机映射 33401)。

### Q5: 怎么彻底重置?
```bash
docker compose down -v    # 删数据卷
docker compose build      # 重新构建镜像
docker compose up -d
```

## 9. 下一步

按 M0 → M7 阶段实现:
- ✅ M0: 骨架 + 部署(已完成)
- ⏳ M1: 用户/租户/权限完善
- ⏳ M2: 产品/物模型
- ⏳ M3: 协议层(MQTT/TCP 真实实现)
- ⏳ M4: 实时 + 历史数据
- ⏳ M5: 规则引擎 + 告警
- ⏳ M6: 设备管理 + 大屏
- ⏳ M7: 联调 + 文档

详见 `docs/STATUS.md`。
