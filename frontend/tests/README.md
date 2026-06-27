# 前端测试

本目录包含 `iot-platform-frontend` 项目的所有测试代码与配置。

## 目录结构

```
tests/
├── setup.ts                # Vitest 全局 setup(mock + 扩展)
├── unit/                   # 单元 + 组件测试
│   ├── stores/
│   │   └── user.spec.ts   # 验证 bug 修复: token 从 cookie 恢复
│   ├── utils/
│   │   └── auth.spec.ts   # cookie 工具测试
│   └── components/
│       └── LoginForm.spec.ts  # 组件测试示例
└── e2e/                    # Playwright 端到端测试
    ├── auth-persistence.spec.ts  # 登录持久化 bug 回归
    └── router-navigation.spec.ts # 路由切换 bug 回归
```

## 快速开始

```bash
# 1. 安装依赖(包含 vitest / playwright)
npm install

# 2. 单元测试
npm run test:run              # 跑一次
npm run test                  # watch 模式
npm run test:coverage         # 带覆盖率

# 3. E2E 测试
npm run e2e:install           # 首次需要安装浏览器
npm run e2e                   # headless 跑全部
npm run e2e:ui                # 打开 Playwright UI
npm run e2e:headed            # 有头浏览器(本地调试用)

# 4. 一键全跑
npm run verify
```

## Bug 回归测试

### Bug 1: 刷新后丢失登录态

**场景**: 用户登录成功后,刷新浏览器,被踢回 `/login`。

**根因**: `src/stores/user.ts` 中 `token` 初始化为 `ref<string>('')`,
未从 `getToken()`(即 cookie) 恢复。

**修复**:

```ts
// src/stores/user.ts
const token = ref<string>(getToken() ?? '')  // 从 cookie 恢复
```

**测试覆盖**:

- `tests/unit/stores/user.spec.ts` — 单元测试验证 store 初始化时从 cookie 恢复
- `tests/e2e/auth-persistence.spec.ts` — E2E 模拟刷新场景

### Bug 2: 路由切换卡死

**场景**: 点击工作台菜单后,再点击其他菜单(如设备列表),主体内容不切换。

**测试覆盖**:

- `tests/e2e/router-navigation.spec.ts` — 验证连续切换路由 URL 跟随变化

## 技术栈

| 工具 | 用途 |
|---|---|
| **Vitest** | 单元测试框架(与 Vite 同源,启动快) |
| **@vue/test-utils** | Vue 组件测试 |
| **happy-dom** | 轻量级 DOM 模拟环境 |
| **@vitest/coverage-v8** | 基于 V8 的覆盖率统计 |
| **Playwright** | E2E 端到端测试(支持多浏览器) |

## 配置要点

- `vite.config.ts` — 已合并 `test` 块,设置 globals/happy-dom/setupFiles
- `tsconfig.json` — 已包含 `vitest/globals`、`@vue/test-utils` 类型
- `playwright.config.ts` — 启动 dev server,配置浏览器矩阵
- `tests/setup.ts` — 全局 mock(js-cookie/nprogress/element-plus)

## CI 集成建议

```yaml
# .github/workflows/test.yml
- run: npm run test:run
- run: npm run e2e
  env:
    CI: true
```
