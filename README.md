# 物联网平台 (IoT Platform)

通用型物联网平台:支持 MQTT / TCP 设备接入,物模型、实时/历史数据、规则引擎、可视化大屏,响应式 Web 支持手机端访问。

## 技术栈

| 层 | 选型 |
|---|---|
| 前端 | Vue 3 + Vite + TypeScript + Element Plus + ECharts + Pinia |
| 后端 | Spring Boot 3 + Java 17 + MyBatis-Plus |
| 业务库 | MySQL 8 |
| 时序库 | TDengine 3 |
| 协议 | MQTT(EMQX 5) / TCP(Netty 4) |
| 鉴权 | JWT + Spring Security(多租户) |
| 部署 | Docker Compose(单机) |

## 目录结构

```
iot-platform/
├── backend/          # Spring Boot 后端
├── frontend/         # Vue 3 前端
├── deploy/           # Docker Compose 部署
├── docs/             # 设计/接口文档
└── scripts/          # 工具脚本
```

## 快速开始

```bash
# 1. 启动所有服务(MySQL / TDengine / EMQX / Backend / Frontend)
cd deploy
docker compose up -d

# 2. 访问
# 前端:  http://localhost:8080
# 后端:  http://localhost:9000/api  (Swagger: /api/swagger-ui.html)
# EMQX:  http://localhost:18083  (admin/public)

# 3. 默认账号
# admin / 123456
```

## 开发模式

```bash
# 后端
cd backend
./mvnw spring-boot:run

# 前端
cd frontend
npm install
npm run dev
```

## 端口规划

> 全部从 33400 起算,避免和本机其他服务冲突。

| 服务 | 主机端口 |
|---|---|
| Frontend | 33400 |
| Backend API | 33401 |
| MySQL | 33402 |
| TDengine RESTful | 33403 |
| TDengine Native | 33404 |
| MQTT TCP | 33405 |
| MQTT SSL | 33406 |
| MQTT WebSocket | 33407 |
| MQTT WSS | 33408 |
| EMQX Dashboard | 33409 |
| TCP Server (后端内嵌) | 33410 |

## 文档

- [架构设计](docs/architecture.md)
- [协议抽象层](docs/protocol.md)
- [物模型规范](docs/thing-model.md)
- [API 文档](docs/api.md) — 启动后端后访问 Swagger UI

## 许可

MIT
