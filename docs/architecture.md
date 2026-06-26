# 架构设计

## 1. 总体架构

```
┌──────────────────────────────────────────────────────────────────┐
│                          设备端(传感器/网关)                       │
└────────────┬──────────────────────────────────┬──────────────────┘
             │ MQTT(33405)                      │ TCP(33410)
             ▼                                  ▼
   ┌──────────────────┐              ┌──────────────────┐
   │   EMQX 5 Broker  │              │  Netty TCP Server│
   └────────┬─────────┘              └────────┬─────────┘
            │ 主题订阅                        │
            ▼                                 ▼
   ┌──────────────────────────────────────────────────┐
   │         协议适配层(ProtocolAdapter SPI)          │
   │  - MqttProtocolAdapter  (HiveMQ MQTT Client)     │
   │  - TcpProtocolAdapter   (Netty 自定义帧)         │
   │  - 解析 → 归一化为 DeviceMessage                  │
   └──────────────────────┬───────────────────────────┘
                          ▼
   ┌──────────────────────────────────────────────────┐
   │                  消息分发中心                      │
   │  - ProtocolDispatcher                             │
   │  - 在线设备 session 索引                          │
   └────┬─────────────┬─────────────┬─────────────┬───┘
        ▼             ▼             ▼             ▼
   ┌────────┐   ┌──────────┐   ┌────────┐   ┌────────┐
   │设备影子│   │ 规则引擎 │   │时序入库│   │事件入库│
   │(MySQL) │   │(SpEL)   │   │(TDengine)│  │(MySQL)│
   └────────┘   └────┬─────┘   └────────┘   └────────┘
                     │
                     ├─→ 告警(站内信/钉钉/微信/Webhook)
                     └─→ 设备指令下行(反向协议层)
   
   ┌──────────────────────────────────────────────────┐
   │           Spring Boot 3 RESTful API              │
   │   + Spring Security + JWT + 多租户                │
   │   + WebSocket(实时数据推送)                       │
   └──────────────────────┬───────────────────────────┘
                          ▼
   ┌──────────────────────────────────────────────────┐
   │       Vue 3 + Element Plus + ECharts             │
   │       响应式 Web(支持手机端)                      │
   └──────────────────────────────────────────────────┘
```

## 2. 模块划分

| 模块 | 路径 | 职责 |
|---|---|---|
| 协议层 | `protocol/core` | 抽象接口 + 统一消息模型 |
| 协议-MQTT | `protocol/mqtt` | MQTT 协议实现(连 EMQX) |
| 协议-TCP | `protocol/tcp` | Netty 自定义帧协议 |
| 产品/物模型 | `product` | 产品 CRUD、JSON 物模型 |
| 设备 | `device` | 设备生命周期、分组、影子 |
| 数据 | `datamanage` | 实时/历史查询 |
| 规则引擎 | `rule` | SpEL 规则 + 动作执行 |
| 告警 | `alert` | 告警记录、通知 |
| 大屏 | `dashboard` | 数据聚合、ECharts |
| 系统 | `system` | 用户/角色/菜单/租户 |

## 3. 数据库

- **MySQL 8**: 业务数据(用户、角色、产品、设备、规则、告警等)
- **TDengine 3**: 时序数据(属性上报历史、事件历史)
  - `iot_prop_int/bigint/double/bool/string`: 按物模型属性类型分超级表
  - 子表按 `(device_id, identifier)` 自动创建
  - 单设备单属性查询通过 tag 过滤毫秒级响应

## 4. 协议抽象层(关键设计)

### 4.1 核心接口

```java
public interface ProtocolAdapter {
    String getName();                            // mqtt / tcp / http
    void start();
    void stop();
    boolean isRunning();
    void setMessageHandler(MessageHandler h);
    boolean sendDownMessage(DeviceSession s, Map<String,Object> msg);
}
```

### 4.2 消息归一化

所有协议解析后都转成 `DeviceMessage`:

```json
{
  "messageId": "uuid",
  "protocol": "mqtt",
  "deviceKey": "device-001",
  "productKey": "temp-sensor",
  "type": "PROPERTY_REPORT",
  "timestamp": 1700000000000,
  "payload": {
    "temperature": 25.6,
    "humidity": 60.2
  }
}
```

### 4.3 扩展新协议

1. 实现 `ProtocolAdapter`
2. 加 `@Component` 让 Spring 自动发现
3. 协议层把上行解析为 `DeviceMessage` 调用 `MessageHandler`
4. 下行用 `Map<String,Object>` 表示意图,由适配器转为协议帧

## 5. 多租户

- **租户隔离**: 所有业务表带 `tenant_id` 字段
- **请求上下文**: `TenantContext` (ThreadLocal) 由 JWT 过滤器注入
- **SQL 注入**: MyBatis-Plus 多租户插件可在 service 层通过 `@TenantIgnore` 关闭(用于超管/字典)

## 6. 权限模型(RBAC)

```
用户 ─(N:N)─ 角色 ─(N:N)─ 菜单(权限)
```

- `sys_user`: 用户
- `sys_role`: 角色(SUPER_ADMIN / TENANT_ADMIN / NORMAL)
- `sys_menu`: 菜单(类型:目录/菜单/按钮)
- `sys_user_role` / `sys_role_menu`: 关联表
- 前端通过 `permission` 字段做按钮级控制
- 后端通过 `@PreAuthorize("hasAuthority('device:add')")` 注解

## 7. 规则引擎

### 7.1 触发器

| 类型 | 说明 |
|---|---|
| `data:received` | 设备数据到达(属性/事件) |
| `device:online` | 设备上线 |
| `device:offline` | 设备离线 |
| `timer:cron` | 定时(Cron 表达式) |

### 7.2 过滤条件(SpEL)

```spel
device.productKey == 'temp-sensor' && payload.temperature > 30
```

可访问变量:
- `device`: Device 对象(productKey/deviceKey/tenantId)
- `payload`: 物模型负载
- `tenantId`: 租户 ID

### 7.3 动作

| 类型 | 说明 |
|---|---|
| `alert` | 写告警记录 |
| `dingtalk` | 钉钉机器人 |
| `wechat` | 企业微信 |
| `webhook` | HTTP 转发 |
| `device:invoke` | 反向下发设备服务 |

## 8. 实时数据推送

- 后端: `WebSocketHandler` + 主题订阅
- 前端: `WebSocket` 客户端
- 路径: `/api/ws/iot?deviceKey=xxx`
- 心跳: 30s
- 鉴权: 子协议 `token` 传 JWT

## 9. 部署拓扑

单机 Docker Compose 部署:
单机 Docker Compose 部署,端口全部从 33400 起算(避免和本机其他服务冲突):

| 服务 | 容器端口 | 主机端口 |
|---|---|---|
| Frontend (Nginx) | 80 | **33400** |
| Backend (Spring Boot) | 9000 | **33401** |
| MySQL 8 | 3306 | **33402** |
| TDengine RESTful | 6041 | **33403** |
| TDengine Native | 6030 | **33404** |
| MQTT TCP | 1883 | **33405** |
| MQTT SSL | 8883 | **33406** |
| MQTT WebSocket | 8083 | **33407** |
| MQTT WSS | 8084 | **33408** |
| EMQX Dashboard | 18083 | **33409** |
| TCP Server (Netty,后端内嵌) | 33410 | **33410** |
