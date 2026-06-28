# 贡献指南 (Contributing Guide)

感谢你愿意为 IoT 平台贡献代码！本文档说明本项目的开发流程、规范与协作约定。

## 📋 目录

1. [行为准则](#行为准则)
2. [开发环境搭建](#开发环境搭建)
3. [代码规范](#代码规范)
4. [提交规范](#提交规范)
5. [PR 流程](#pr-流程)
6. [测试要求](#测试要求)
7. [文档要求](#文档要求)

---

## 行为准则

本项目采用 [Contributor Covenant 2.1](https://www.contributor-covenant.org/zh-cn/version/2/1/code_of_conduct/) 作为行为准则。所有贡献者必须尊重、包容、专业地对待他人。

---

## 开发环境搭建

### 前置依赖

- **Node.js 20+** + npm（前端）
- **JDK 17+** + Maven 3.9+（后端）
- **Docker + Docker Compose**（中间件：MySQL 8 / TDengine 3 / EMQX 5）
- **Make**（Linux/macOS 原生，Windows 需 WSL 或 Git Bash）

### 一键启动

```bash
# 克隆仓库
git clone <repo-url> iot-pt && cd iot-pt

# 启动中间件（首次约 1-2 分钟）
make dev-infra

# 启动后端（新终端）
make dev-backend

# 启动前端（新终端）
make dev-frontend
```

访问：
- 前端开发服务器：http://localhost:33411
- 后端 API：http://localhost:33412/api
- Swagger UI：http://localhost:33412/api/doc.html
- EMQX Dashboard：http://localhost:33409（admin/public）

### 全栈容器化（含 Remote Debug）

```bash
make dev-all       # 5005 端口可挂 JDWP
make dev-down      # 停止
```

---

## 代码规范

### 前端（Vue 3 + TypeScript）

| 维度 | 工具 | 命令 |
|---|---|---|
| 类型检查 | `vue-tsc` | `npm run type-check` |
| Lint | ESLint 9 (flat config) | `npm run lint` / `npm run lint:fix` |
| 样式 Lint | stylelint | `npm run lint:css` |
| 格式化 | Prettier 3 | `npm run format` |
| 测试 | Vitest | `npm run test:run` |
| E2E | Playwright | `npm run e2e` |

**强制要求**：
- 不提交 `console.log/debug`（用 `console.warn/error` 或 logger）
- 不使用 `any`（除非明确的迁移期标注）
- 不使用 `!` 非空断言（除非有充分理由）
- 一律使用 ES Module `import type` 导入类型

### 后端（Spring Boot 3 + Java 17）

| 维度 | 工具 |
|---|---|
| 编译 | `mvn -B clean package` |
| 测试 | `mvn -B verify`（surefire + failsafe + jacoco） |
| 覆盖率 | JaCoCo，渐进式 60% → 80% |

**强制要求**：
- 公共 API 显式声明类型（参数 + 返回值）
- DTO 使用 `record`
- 异常使用领域异常（继承 `RuntimeException`），不滥用 `catch (Exception)`
- SQL 全部走参数化（MyBatis-Plus `LambdaQueryWrapper`）
- 密码 bcrypt 编码，不写日志

---

## 提交规范

严格遵循 [Conventional Commits](https://www.conventionalcommits.org/zh-hans/v1.0.0/) 规范。格式：

```
<type>(<scope>): <subject>

<body>

<footer>
```

允许的 type：`feat` `fix` `refactor` `docs` `test` `chore` `perf` `ci` `style` `build` `revert`

**commitlint 已自动校验**：
- 标题最长 72 字符
- type/scope 必须小写
- 必须落在白名单 type 列表内

提交前会自动跑 lint-staged（ESLint + Prettier + stylelint）。

---

## PR 流程

### 1. 创建分支

```bash
git checkout -b feat/<scope>-<short-desc>
# 例如: feat/monitor-add-cpu-chart
```

### 2. 开发与测试

- 写代码 + 写测试（TDD 优先）
- 跑 `npm run verify`（前端全链路）/ `mvn verify`（后端）
- 提交时 husky 自动触发 lint-staged + commitlint

### 3. 推送并开 PR

```bash
git push -u origin feat/<scope>-<short-desc>
```

- 在 GitHub 上开 PR，**目标分支选 `main`**
- 填写 `.github/PULL_REQUEST_TEMPLATE.md` 给的 checklist
- 关联 issue（如有）

### 4. CI 门禁

PR 必须通过：
- ✅ 前端：type-check + lint + lint:css + test:run + build:check
- ✅ 后端：mvn verify + jacoco 覆盖率 ≥ 60%
- ✅ gitleaks：无密钥泄露
- ✅ Dependabot：当前 PR 不阻塞

### 5. Code Review

- 至少 1 名维护者 review
- 关键路径（auth / DB / API / 部署配置）必须 2 人 review
- 全部 CI 绿灯 + approve 后 squash merge

---

## 测试要求

| 层级 | 框架 | 覆盖目标 |
|---|---|---|
| 单元测试（前端） | Vitest | 60% → 80%（渐进） |
| 单元测试（后端） | JUnit 5 + Mockito | 60% → 80%（渐进） |
| 集成测试（后端） | Spring Boot Test + Testcontainers | 关键路径 100% |
| E2E（前端） | Playwright | 核心用户流程 |

**AAA 模式**（Arrange / Act / Assert）：

```typescript
test('计算余弦相似度', () => {
  // Arrange
  const v1 = [1, 0, 0]
  const v2 = [0, 1, 0]
  // Act
  const sim = cosine(v1, v2)
  // Assert
  expect(sim).toBe(0)
})
```

---

## 文档要求

- **新增模块** → 更新 `docs/architecture.md` + 必要时写 ADR (`docs/adr/`)
- **API 变更** → 更新 `docs/api.md` + 同步 Swagger 注解
- **部署变更** → 更新 `deploy/README.md` + `.env.example`
- **破坏性变更** → 在 PR body 单独列出"Breaking Changes"

---

## 寻求帮助

- 🐛 Bug 报告：开 GitHub Issue
- 💡 功能建议：开 GitHub Discussion
- 🔒 安全问题：见 [SECURITY.md](./SECURITY.md)
- 💬 一般问题：项目内 IM 群

再次感谢你的贡献！🎉