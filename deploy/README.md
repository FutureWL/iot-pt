# 部署 (Deployment)

> IoT 平台完整部署指南(开发/生产/监控)

## 架构概览

```
┌─────────────────────────────────────────────────────────────┐
│                       Frontend (Nginx)                      │
│                          :33411                            │
└────────────────────────────┬────────────────────────────────┘
                             │ /api/*
                             ▼
┌─────────────────────────────────────────────────────────────┐
│                    Backend (Spring Boot)                    │
│         :9000 (容器内) → :33412 (主机映射)                  │
│         + actuator: /api/actuator/{health,prometheus,...}   │
└────┬────────────┬────────────┬────────────┬────────────────┘
     │            │            │            │
     ▼            ▼            ▼            ▼
  ┌──────┐   ┌────────┐  ┌────────┐  ┌──────┐
  │MySQL │   │TDengine│  │ Redis  │  │ EMQX │
  │:33402│   │:33403  │  │:33413  │  │:33405│
  └──────┘   └────────┘  └────────┘  └──────┘

  ┌──────────────────┐  ┌──────────┐
  │  Prometheus      │→ │ Grafana  │
  │     :33420       │  │  :33421  │
  └──────────────────┘  └──────────┘
```

## 端口规划

| 服务 | 容器内 | 主机 | 说明 |
|---|---|---|---|
| MySQL | 3306 | 33402 | 业务库 |
| TDengine | 6041 | 33403 | 时序库 |
| EMQX | 1883 | 33405 | MQTT broker |
| EMQX Dashboard | 18083 | 33409 | EMQX 控制台 |
| Redis | 6379 | 33413 | 缓存 |
| Backend | 9000 | 33412 | Spring Boot |
| Frontend | 80 | 33411 | Nginx |
| Prometheus | 9090 | 33420 | 指标 |
| Grafana | 3000 | 33421 | 仪表板 |

## 快速启动 (开发)

```bash
# 1. 启动所有服务
docker compose -f deploy/docker-compose.yml up -d

# 2. 跑数据库迁移
for f in deploy/mysql/migration/V*.sql; do
  docker exec -i iot-mysql mysql -uiot -piot123456 iot_platform < "$f"
done

# 3. 访问
# 前端:      http://localhost:33411
# 后端 API:  http://localhost:33412
# 默认账号:  admin / 123456
```

## 启动监控栈 (可选)

```bash
docker compose -f deploy/docker-compose.yml up -d prometheus grafana
# Prometheus:  http://localhost:33420
# Grafana:     http://localhost:33421  (admin/admin)
#             → Dashboards → "IoT" → "IoT 平台后端概览"
```

## 镜像构建 (生产部署)

```bash
# 后端(多阶段构建,只构建 iot-api 模块)
docker build -t iot-platform/backend:1.0.0 -f backend/Dockerfile backend

# 前端
docker build -t iot-platform/frontend:1.0.0 -f frontend/Dockerfile frontend \
  --build-arg VITE_API_BASE_URL=/api \
  --build-arg VITE_APP_TITLE=物联网平台

# 推到私有仓库
docker push your-registry.com/iot-platform/backend:1.0.0
docker push your-registry.com/iot-platform/frontend:1.0.0
```

## 关键配置

### 后端环境变量
```bash
SPRING_PROFILES_ACTIVE=prod
MYSQL_HOST=mysql
MYSQL_PORT=3306
MYSQL_DB=iot_platform
MYSQL_USER=iot
MYSQL_PASSWORD=iot123456
TDENGINE_HOST=tdengine
TDENGINE_PORT=6041
TDENGINE_USER=root
TDENGINE_PASSWORD=taosdata
REDIS_HOST=redis
REDIS_PORT=6379
MQTT_BROKER=tcp://emqx:1883
MQTT_USERNAME=emqx_user
MQTT_PASSWORD=emqx_pass
JWT_SECRET=<32+ bytes>
JAVA_OPTS="-Xms512m -Xmx1024m"
```

### 持久化
8 个 named volume:
- `mysql_data`       - MySQL 数据
- `tdengine_data`    - TDengine 数据/日志
- `emqx_data/log`    - EMQX 数据/日志
- `redis_data`       - Redis 持久化
- `backend_logs`     - 后端日志
- `backend_upload`   - 后端上传文件
- `prometheus_data`  - Prometheus 时序数据
- `grafana_data`     - Grafana 仪表板/用户

### 安全
- JWT secret: **必须** 通过 `JWT_SECRET` env 注入(不能默认)
- MQTT 凭据: 通过 `MQTT_USERNAME` / `MQTT_PASSWORD` 注入
- EMQX dashboard 密码: `EMQX_DASHBOARD_PASSWORD`
- Grafana admin: `GRAFANA_USER` / `GRAFANA_PASSWORD`
- Spring Security: `/actuator/prometheus` 在白名单(供 Prometheus 抓取)

## 关闭/清理

```bash
# 优雅停止(保留数据)
docker compose -f deploy/docker-compose.yml stop

# 关闭 + 删容器(保留数据)
docker compose -f deploy/docker-compose.yml down

# 彻底清理(删容器 + 删数据)
docker compose -f deploy/docker-compose.yml down -v
```

## 健康检查

| 端点 | 检查内容 | K8s 用法 |
|---|---|---|
| `/api/actuator/health` | 综合状态(详情) | 外部 LB |
| `/api/actuator/health/liveness` | 进程是否活 | livenessProbe |
| `/api/actuator/health/readiness` | 是否就绪 | readinessProbe |
| `/api/actuator/prometheus` | Prometheus 抓取端点 | scrape |

## 故障排查

### 后端连不上 MySQL
```bash
docker exec -it iot-backend bash
# 容器内测
nc -zv mysql 3306
# 看错误
docker logs iot-backend | grep -i "mysql\|jdbc"
```

### TDengine 写不进
```bash
docker logs iot-tdengine | tail
# 应用层会 fallback:TDengine 写失败 → 仅写 MySQL(损失时序数据)
```

### 端口冲突
改 `.env.dev` / `.env.prod` 里的 `*_PORT` 变量,重新 up。

## 性能调优

### JVM
```bash
# 默认 -Xms512m -Xmx1024m,生产改大
JAVA_OPTS="-Xms2g -Xmx4g -XX:+UseG1GC -XX:MaxRAMPercentage=75"
```

### HikariCP (MySQL)
```yaml
spring.datasource.hikari:
  maximum-pool-size: 50    # 默认 20
  minimum-idle: 10
  connection-timeout: 30000
```

### Tomcat
```yaml
server.tomcat.threads:
  max: 400   # 默认 200
  min-spare: 20
```