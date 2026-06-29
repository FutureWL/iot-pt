# 可观测性 (Observability)

> 本项目的监控、告警、性能分析指南

## 三大支柱

| 维度 | 工具 | 端点 / 配置 |
|---|---|---|
| **指标 (Metrics)** | Prometheus + Grafana | `/api/actuator/prometheus` |
| **健康 (Health)** | Spring Boot Actuator | `/api/actuator/health` (含 liveness/readiness 探针) |
| **日志 (Logs)** | Logback (默认 stdout) | `slow.sql` logger 慢 SQL 单独通道 |

## 快速开始

```bash
# 启动完整监控栈
docker compose -f deploy/docker-compose.yml up -d backend prometheus grafana

# 访问
# Prometheus:    http://localhost:33420
# Grafana:       http://localhost:33421  (admin / admin)
# Backend:       http://localhost:33412/api/actuator/prometheus
# Health:        http://localhost:33412/api/actuator/health
```

## Prometheus 关键指标

### HTTP 请求
```
http_server_requests_seconds_count   # 请求计数(可按 method/status/uri 拆)
http_server_requests_seconds_sum     # 总耗时
http_server_requests_seconds_bucket  # 直方图(支持 p50/p95/p99)
```

### JVM
```
jvm_memory_used_bytes{area="heap"}    # Heap 使用
jvm_memory_max_bytes{area="heap"}     # Heap 上限
jvm_gc_pause_seconds                  # GC 暂停
process_cpu_usage                     # 进程 CPU
system_load_average_1m                # 系统负载
```

### 数据库
```
hikaricp_connections_active           # 活跃连接
hikaricp_connections_idle             # 空闲连接
hikaricp_connections_pending          # 等待连接
hikaricp_connections_max              # 连接池上限
```

### HTTP 慢 SQL
- 阈值:`iot.sql.slow-threshold-ms`(默认 500ms)
- 日志 logger:`slow.sql`
- 启用:已默认开,可通过 `application.yml` 调整

## Grafana 仪表板

启动后,打开 Grafana → Dashboards → "IoT" 文件夹 → "IoT 平台后端概览":

- 4 个核心 stat:RPS / p95 / p99 / 5xx 错误率
- 2 个时序图:按状态码分组的请求速率、按 URI 的 p95
- 3 个资源 stat:JVM Heap、CPU、HikariCP 连接池

(在 provisioning/dashboards 自动加载)

## 告警建议(Prometheus Alertmanager)

```yaml
# prometheus/alerts.yml
groups:
  - name: iot-backend
    rules:
      - alert: HighErrorRate
        expr: sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count[5m])) > 0.05
        for: 5m
        annotations:
          summary: "5xx 错误率超过 5%"

      - alert: HighP99Latency
        expr: histogram_quantile(0.99, sum by (le) (rate(http_server_requests_seconds_bucket[5m]))) > 2
        for: 5m
        annotations:
          summary: "P99 响应时间 > 2s"

      - alert: JVMHeapHigh
        expr: jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} > 0.85
        for: 5m
        annotations:
          summary: "JVM Heap 使用率 > 85%"

      - alert: HikariPoolExhausted
        expr: hikaricp_connections_pending > 0
        for: 1m
        annotations:
          summary: "数据库连接池等待中(可能耗尽)"
```

## K8s 探针配置

```yaml
livenessProbe:
  httpGet: { path: /api/actuator/health/liveness, port: 9000 }
  initialDelaySeconds: 60
  periodSeconds: 15

readinessProbe:
  httpGet: { path: /api/actuator/health/readiness, port: 9000 }
  initialDelaySeconds: 30
  periodSeconds: 10
```

## 慢 SQL 排查

```bash
# 实时跟慢 SQL
docker logs -f iot-backend | grep "slow.sql"

# 输出格式: cost=523ms | sql=SELECT ... | params={...}
```

阈值可在 `application.yml` 调整:
```yaml
iot:
  sql:
    slow-threshold-ms: 500   # 调到 200 看更细的慢查询
```

## 调试技巧

### 1. 临时改指标采集粒度
```yaml
management:
  metrics:
    distribution:
      percentiles-histogram: { http.server.requests: true }
      percentiles: { http.server.requests: 0.5, 0.95, 0.99 }
```

### 2. 临时开启慢 SQL DEBUG
```yaml
logging:
  level:
    slow.sql: DEBUG  # 把所有 SQL 都打到 slow.sql logger(生产慎用)
```

### 3. 看某接口的 p99
```promql
histogram_quantile(0.99,
  sum by (le) (
    rate(http_server_requests_seconds_bucket{uri="/data/history"}[5m])
  )
)
```

## 历史

- 2026-06-29: Prometheus 接入 + Grafana 仪表板 + 慢 SQL 拦截器
- 2026-06-29: K8s liveness/readiness 探针(spring boot 2.3+ 内置)
- 2026-06-29: actuator security 白名单(/actuator/prometheus 等)