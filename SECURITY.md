# 安全策略 (Security Policy)

## 支持版本

下表列出当前获得安全更新的版本：

| 版本 | 支持状态 | 说明 |
|---|---|---|
| `1.0.0-SNAPSHOT` | ✅ 当前开发版 | 持续接收安全更新 |
| `1.0.0` 及以上正式版 | ✅ 维护中 | 接收 CVE 修复 |
| `< 1.0.0` | ❌ 已停止支持 | 强烈建议升级 |

> 注：项目仍在 1.0.0 之前的快速迭代期，预发布版本可能包含实验性安全特性。

---

## 报告漏洞

我们重视每一个安全报告。请**不要**通过公开 issue 报告漏洞。

### 首选渠道：GitHub Security Advisories

1. 访问 https://github.com/<owner>/iot-pt/security/advisories/new
2. 填写漏洞详情（影响范围、复现步骤、潜在危害）
3. 提交后会进入私有讨论区

### 备选渠道：私密邮件

发送至仓库所有者邮箱（见 GitHub profile）。邮件主题请加 `[SECURITY]` 前缀。

### 应包含的信息

- 漏洞类型（XSS / SQL 注入 / 认证绕过 / SSRF / ...）
- 受影响组件（前端 / 后端 / 部署 / 第三方依赖）
- 复现步骤（PoC 代码或截图）
- 影响范围（影响哪些数据 / 哪些用户）
- 你的修复建议（可选）

---

## 我们的承诺

- **48 小时内**确认收到报告
- **7 天内**评估严重等级并给出修复计划
- **30 天内**发布修复（CRITICAL 漏洞优先）
- 修复发布后致谢（除非你希望匿名）

---

## 已知安全问题（透明披露）

> 本节列出当前已知的、计划在后续版本修复的安全项。

### ⚠️ HIGH

| 问题 | 影响 | 计划修复 |
|---|---|---|
| **默认 JWT secret 写死在 `application.yml`** | 部署时若未替换 `JWT_SECRET` 环境变量，攻击者可伪造 token | 下个 sprint 强制启动时校验 secret 长度与熵，失败则拒绝启动 |
| **CORS `allowedOriginPatterns("*")`** | 当前允许任意来源跨域调用 | 待前端部署域名清单确认后改为白名单 |
| **`/actuator/health` 放白名单但 actuator 未启用** | 当前端点 404，无健康探针 | C4 提交会启用 actuator + 配置 `show-details=when-authorized` |

### ⚠️ MEDIUM

| 问题 | 影响 | 计划修复 |
|---|---|---|
| **无 rate limit** | 登录/接口可被暴力枚举 | 后续接入 Bucket4j（应用层）或 nginx `limit_req`（网关层） |
| **nginx 无 CSP / X-Frame-Options / 等安全响应头** | 缺少基础浏览器侧防护 | C3 提交会加 5 个标准响应头 |
| **前端无 Sentry 类错误监控** | 客户端异常不可见 | 下一阶段评估 |
| **`mybatis-plus.configuration.log-impl=StdOutImpl`** | 生产 SQL 输出到 stdout，可能泄露数据 | 下个 sprint 改为 SLF4JImpl |

### ℹ️ LOW

| 问题 | 影响 | 计划修复 |
|---|---|---|
| **README 端口规划与 .env.dev 不完全一致** | 文档误导 | 文档同步任务 |
| **客户文档目录 `customer-requirements/` 在 .gitignore 但本地留存** | 需用私有仓库或加密备份管理 | 流程问题 |

---

## 安全最佳实践（部署侧）

- **替换所有默认密钥**：参考 `deploy/.env.example` 列出的所有 `*_PASSWORD` / `JWT_SECRET`
- **不要将 `.env.prod` 提交到仓库**
- **启用 HTTPS**：见 `deploy/nginx/` 后续配置（待补）
- **定期轮换** 数据库密码 / JWT secret（建议 90 天）
- **启用 MySQL/TDengine 容器外的数据卷备份**
- **监控异常登录**：当前 `/auth/login` 无审计日志，下个 sprint 加
- **保持依赖更新**：Dependabot 已配置，每周自动开升级 PR

---

## 致谢

感谢以下安全研究人员的负责任披露（暂无）。