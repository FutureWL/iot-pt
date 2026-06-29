# E2E 测试指南

> 本项目 E2E 全部走**真后端**(`/api/auth/login` 拿真 token,后续页面请求带真 token)

## 设计目标

- **单一策略**:所有 E2E 都用真后端(2026-06-29 统一)
- **生产 bug 暴露**:真后端能发现 mock 模式发现不了的问题(如本项目的 Snowflake id 精度、alert/center handle 端点缺失、dict/org 后端 stub)
- **可关闭**:`E2E_REAL_BACKEND=false` 可切回纯前端调试(此时会 skip 所有需要后端的 test)

## 架构

```
tests/e2e/
├── _helpers.ts              ← 公共 helper (loginAsAdmin / api / waitForBackend)
├── knowledge-crud.spec.ts   ← 8 个真集成 spec
├── workorder-list.spec.ts
├── device-list.spec.ts
├── system-role.spec.ts
├── alert-center.spec.ts
├── system-user.spec.ts
├── system-dict.spec.ts
├── system-organization.spec.ts
├── auth-persistence.spec.ts ← 6 个 pre-existing 改的真集成 spec
├── router-navigation.spec.ts
├── data-history.spec.ts
├── iot-console.spec.ts
├── sse-only.spec.ts
└── sse-push.spec.ts
```

## helpers API

```typescript
// 1. 拿真 token + 写 cookie
await loginAsAdmin(context)  // 返回 token

// 2. 启动时确认后端就绪
const ready = await waitForBackend()
if (!ready) test.skip(true, '后端未就绪, 跳过')

// 3. 带 token 的 fetch(用于"先 API 造数据,再 UI 验证"模式)
const json = await api('POST', '/api/knowledge', body, token)
const id = json.data.id
// ...稍后用 UI 删除

// 4. env 开关
process.env.E2E_REAL_BACKEND === 'false'  // 跳过所有需要后端的 test
```

## 标准 spec 模板

```typescript
import { test, expect } from '@playwright/test'
import { loginAsAdmin, REAL_BACKEND, waitForBackend, BASE_URL } from './_helpers'

const TEST_TIMEOUT = 60_000  // 真后端 + 多次 API 调

test.describe('XXX 真集成', () => {
  test.beforeEach(async ({ context }) => {
    test.setTimeout(TEST_TIMEOUT)
    if (!REAL_BACKEND) test.skip(true, 'E2E_REAL_BACKEND=false, skip real backend test')
    const ready = await waitForBackend()
    if (!ready) test.skip(true, '后端未就绪, 跳过')
    await loginAsAdmin(context)
  })

  test('XXX', async ({ page }) => {
    await page.goto(`${BASE_URL}/#/xxx`)
    // ... 真后端会自动处理
  })
})
```

## 调试技巧

### 1. 截屏
```typescript
await page.screenshot({ path: 'test-results/my-debug.png', fullPage: true })
```

### 2. 监听 API 响应
```typescript
page.on('response', async (resp) => {
  if (resp.url().includes('/api/xxx') && resp.request().method() === 'POST') {
    console.log(`POST ${resp.url()}: ${resp.status()}`)
  }
})
```

### 3. 验证 response body 而非 HTTP 状态
Playwright `request.X` 总是返 200,真成功要看 `body.code`:
```typescript
const res = await api('POST', '/api/xxx', body, token)
expect(res.code).toBe(200)  // 必须验 body,不是 HTTP status
```

### 4. 精确定位元素
```typescript
// 不用 placeholder(可能没有),用 form-item label:
await page.locator('.el-dialog .el-form-item:has-text("用户名") input').fill(name)

// 不用 substring (hasText 默认子串匹配),用 exact:
await expect(page.getByText('工作台', { exact: true })).toBeVisible()
```

### 5. ModalForm submit-text 不固定
每个 page 可能 override `submit-text`(默认"保存"):
- "创建" — system/Role, system/User
- "创建" — system/Dict type
- "保存" — 默认,dict item,knowledge 等
- "确认" — 某些确认对话框
- "确认重置" — User 重置密码

**最稳的姿势**:在 snapshot 里看实际按钮文字。

## 运行

```bash
# 全跑(默认有后端,21 tests)
npm run e2e

# 跑单个 spec
npx playwright test tests/e2e/knowledge-crud.spec.ts

# 跑单个 test
npx playwright test -g "完整 CRUD"

# 调试模式
npm run e2e:ui

# 关闭后端依赖(本地无后端)
E2E_REAL_BACKEND=false npm run e2e
```

## 历史

- 2026-06-29:统一策略 — 6 个 pre-existing specs 改用真后端 + helpers
- 2026-06-28:knowledge-crud 真集成 E2E(发现 Snowflake id 精度 bug)
- 2026-06-28:alert-center E2E(发现 handle 端点缺失 + 修复)
- 2026-06-28:workorder/device/role E2E 模板建立