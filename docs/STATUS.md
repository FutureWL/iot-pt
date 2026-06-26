# 项目状态(M0 → M7 路线图)

> 记录每个阶段的完成度,方便回看和推进。

## M0 - 基础设施骨架 ✅ 已完成

### 后端
- [x] Spring Boot 3.3 + Java 17 工程
- [x] 协议抽象层(ProtocolAdapter / DeviceMessage / DeviceSession / ProtocolDispatcher)
- [x] MQTT / TCP 协议适配器(占位实现,M3 填充)
- [x] Spring Security + JWT 认证
- [x] 多租户上下文(TenantContext + 拦截器)
- [x] MyBatis-Plus + 多数据源(MySQL + TDengine)
- [x] 统一响应 R / 业务异常 / 全局异常处理
- [x] 最小登录接口(/auth/login, /auth/info, /auth/logout)
- [x] Knife4j(Swagger UI)

### 前端
- [x] Vue 3 + Vite + TypeScript 工程
- [x] Element Plus(自动按需引入)
- [x] 响应式主布局(桌面侧栏 + 移动抽屉菜单)
- [x] 登录页(渐变背景 + 响应式)
- [x] Axios 拦截器(401 自动跳转登录)
- [x] Pinia 用户状态
- [x] 路由守卫(登录态 + 权限)
- [x] 14 个视图占位

### 部署
- [x] Docker Compose(5 服务:MySQL / TDengine / EMQX / Backend / Frontend)
- [x] MySQL 初始化 SQL(13 张业务表 + 初始数据)
- [x] TDengine 初始化 SQL(5 个超级表)
- [x] 后端 Dockerfile(多阶段构建)
- [x] 前端 Dockerfile + Nginx 反向代理配置
- [x] 健康检查

### 文档
- [x] README
- [x] 架构设计文档
- [x] 快速启动指南

## M1 - 用户 / 租户 / 权限 ⏳ 待开发

- [ ] 完整的用户管理 CRUD
- [ ] 角色管理(从数据库动态查询)
- [ ] 菜单管理(树形)
- [ ] 租户管理(超级管理员)
- [ ] 实际从 `sys_user_role` / `sys_role_menu` 关联表查询角色和权限
- [ ] 字典管理(枚举类动态化)
- [ ] 操作日志(可选)

## M2 - 产品 / 物模型 ⏳ 待开发

- [ ] 产品 CRUD
- [ ] 物模型 JSON 编辑器(前端可视化编辑属性/事件/服务)
- [ ] 物模型解析器(后端按产品校验上报数据)
- [ ] 产品分类(Category)
- [ ] 设备认证密钥生成

## M3 - 协议层(关键!) ⏳ 待开发

- [ ] MQTT 适配器:连接 EMQX、订阅通配主题、解析属性/事件/服务回复
- [ ] TCP 适配器:Netty Server、自定义帧协议、握手鉴权
- [ ] 设备自动注册(根据 productKey/deviceKey 查找/创建设备)
- [ ] 设备影子更新(物模型属性当前值)
- [ ] 在线状态维护(心跳 + 离线检测)
- [ ] 下行消息:服务调用 / 属性设置

## M4 - 数据 ⏳ 待开发

- [ ] 实时数据:WebSocket 推送(订阅 deviceKey)
- [ ] 历史数据:TDengine 查询接口(时间范围、降采样)
- [ ] 实时大屏面板
- [ ] 历史图表(ECharts)

## M5 - 规则引擎 / 告警 ⏳ 待开发

- [ ] 规则 CRUD
- [ ] SpEL 表达式解析器(支持 `device` / `payload` / `tenantId`)
- [ ] 触发器:data / online / offline / timer
- [ ] 动作:alert / dingtalk / wechat / webhook / device:invoke
- [ ] 告警列表 + 处理
- [ ] 通知渠道配置(钉钉/微信/Webhook)

## M6 - 设备管理 / 大屏 ⏳ 待开发

- [ ] 设备 CRUD + 分组
- [ ] 设备下发指令(服务调用)
- [ ] 设备详情页(属性、事件、命令、影子、在线状态)
- [ ] 大屏:设备地图 / 实时数据 / 告警统计
- [ ] 设备导入/导出(Excel)

## M7 - 联调 / 优化 ⏳ 待开发

- [ ] 模拟设备测试(MQTT / TCP)
- [ ] 性能压测(几百设备并发)
- [ ] 文档完善(API 文档、使用手册)
- [ ] 一键备份脚本

---

## 立即可做的事(用户视角)

### 启动项目
```bash
cd deploy
docker compose up -d
```
访问 http://localhost:33400,默认 `admin / 123456 / 租户 default` 登录。

### 验证后端
```bash
curl -X POST http://localhost:33401/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"tenantCode":"default","username":"admin","password":"123456"}'
```

### 接下来从哪个模块开始?
1. **M3 协议层**(最关键,平台价值所在)
2. **M2 产品/物模型**(协议层的前置依赖)
3. **M5 规则引擎**(展示平台智能化能力)

**建议路径**: M2 → M3 → M4 → M5 → M6 → M1 补全 → M7
