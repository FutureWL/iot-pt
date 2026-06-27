# 页面巡检报告 — 2026-06-28

## 触发原因

用户反馈 `http://localhost:33411/#/dashboard` 访问报错,要求:

1. 自动巡检所有页面
2. 修复报错
3. 写回归测试
4. 做记录
5. 提交

---

## 巡检范围

**全部 33 个路由**(从 `frontend/src/router/index.ts` 提取):

| 区块 | 路由数 | 列表 |
|---|---|---|
| 工作台 | 1 | dashboard |
| 监测中心 | 7 | device/overview, monitor/{pd, prpd, temperature, environment, gis, topology} |
| 告警与运维 | 5 | alert/center, workorder/{list, detail/:id}, knowledge/{list, editor/:id?}, ops/statistics |
| 产品与设备 | 6 | device/{list, group, shadow}, product, product/thing-model/:id |
| 数据服务 | 3 | data/{realtime, history}, report/center |
| 大屏可视化 | 2 | screen, iot-console |
| 系统管理 | 8 | system/{user, role, menu, tenant, organization, dict, log, notify} |

合计 **32 个业务路由 + 1 个 login + 1 个 404** = 34 条 (排除 404)。

---

## 巡检工具与方法

### 环境

- **后端**: mock token (无法真实登录,但触发所有 view 的 fetch API)
- **dev server**: vite 5.4.21 @ `http://localhost:33414/`
- **认证 mock**: 直接注入 Pinia `userStore` 的 `token + userInfo(roles=['SUPER_ADMIN'])`,绕过登录页 UI

### 探针

通过 `mcp__plugin_ecc_chrome-devtools__evaluate_script` 在浏览器内:

1. 注册 `window.addEventListener('error', ...)` 捕获 `error` 事件
2. 注册 `window.addEventListener('unhandledrejection', ...)` 捕获 Promise rejection
3. Spy `console.error` 捕获红字日志
4. 串行调用 `router.push('/' + path)` + `setTimeout(700ms)` 让页面 fetch + render

每个路由收集: `hasContent` (DOM 是否有内容)、`newUncaught` (新增未捕获异常数)、`newConsoleErrors` (新增 console error 数)、`hasEmpty` (是否有 `<el-empty>` 兜底)。

---

## 发现的问题

### Bug #1 — 全局未捕获 Promise rejection 污染 console(影响 25 个路由)

**症状**:

- 32 个业务路由中 **25 个**触发 `unhandledrejection: AxiosError: Request failed with status code 403`
- DevTools Console 出现大量红字 `Uncaught (in promise)`
- 4 个无 API 路由未触发:`iot-console` (WS)、`device/shadow` (WS)、`system/menu`/`system/tenant`/`system/notify` (无 fetch)

**根因**:

绝大多数 view 的 `load` 函数使用模式:

```ts
async function load() {
  try {
    const [...] = await Promise.all([api1(), api2()])
    ...
  } finally {
    loading.value = false  // 仅资源清理
  }
}
```

`try/finally` 缺 `catch`,API 抛 403 时,异常向上冒泡为 unhandled rejection。

**已部分防御**(`frontend/src/api/request.ts`):

拦截器已 `ElMessage.error` + `Promise.reject`,但 view 仍需自己 catch。

### Bug #2 — workorder/detail/123 渲染失败

**症状**:

`/workorder/detail/123` 页面 `hasContent: false`,渲染后整页空白。

**根因**:

`frontend/src/views/workorder/Detail.vue` 的模板:

```vue
<template>
  <div class="page-container" v-loading="loading">
    <div class="page-header">...</div>
    <template v-if="detail">
      <!-- 所有详情内容 -->
    </template>
  </div>
</template>
```

`detail` 为 null 时(API 失败),模板整个不渲染,只留下 page-header + 空 page-container。
且 `load()` 也是 `try/finally` 缺 `catch`,错误冒泡。

### 已正确处理的路由(无需修复)

- `device/shadow`, `iot-console`: 用 WebSocket,不走 HTTP API
- `system/menu`, `system/tenant`, `system/notify`: 未发现 fetch 调用
- `dashboard` 的 `loadAll`: 已对 `getWorkOrderStats` 和 `getTopologyGraph` 加 `.catch()`,但仍漏了 `getDashboardSummary` — 也被 #1 全局兜底覆盖

---

## 修复

### Fix #1 — 全局 `unhandledrejection` 兜底

**新增**: `frontend/src/utils/error-boundary.ts`

- `isApiError(reason)`: 判断是否为 axios / 网络层异常(基于 `isAxiosError`、`name === 'AxiosError'`、`message.includes('Request failed')` / `'网络异常'`)
- `installUnhandledRejectionGuard()`: 注册 `window.addEventListener('unhandledrejection', ...)`,匹配 API 异常时 `event.preventDefault()` + dev 模式 `console.warn`(prod 静默);其它异常不拦截。

**修改**: `frontend/src/main.ts` mount 后调用 `installUnhandledRejectionGuard()`,删除原内联 handler。

### Fix #2 — workorder/detail.vue 加 catch + 空状态

**修改**: `frontend/src/views/workorder/Detail.vue`

- `load()` 加 `catch {}` 块,把 `detail/logs` 重置为空(避免旧数据残留 + 阻止异常冒泡)
- 模板 `<template v-if="detail">` 块后追加 `<el-empty v-else-if="!loading" description="工单不存在或加载失败,请返回列表重试" />`

### 不在本次范围

未对 25 个 view 逐个加 try/catch — 全局兜底已能消除 console 污染,且保留 dev 模式日志。view 层的具体降级策略由各业务后续按需处理(避免一次性大 refactor)。

---

## 验证结果(修复后)

### 单元测试

- `tests/unit/utils/error-boundary.spec.ts`: **14 个用例** 全部通过
  - 7 个 `isApiError` 分支(axios 标识、name、message 关键词、普通 Error、null/undefined、普通对象)
  - 7 个 `installUnhandledRejectionGuard` 行为(preventDefault、dev warn、网络异常拦截、普通 Error 不拦截、uninstall 解绑、多次 install)
- 全部测试套件: **53/53 通过**(theme 14 + auth 8 + user 8 + LoginForm 3 + ws 6 + error-boundary 14)
- `vue-tsc --noEmit`: **0 错误**

### 端到端验证(33 路由全巡)

| 指标 | 修复前 | 修复后 |
|---|---|---|
| Console errors | **25** (红字) | **0** |
| Uncaught 事件 | 25 | 25 (事实存在,但被静默) |
| 页面渲染失败 | 1 (workorder/detail/123) | 0 |
| 空状态兜底 | 无 | 多数路由显示 `<el-empty>`,workorder/detail 明确提示 |

每个路由 `hasContent` 全部为 true,workorder/detail/123 现在显示 `工单不存在或加载失败,请返回列表重试` 的空状态。

---

## 提交

- `fix(boundary): 全局 unhandledrejection 兜底 + workorder detail 渲染修复`
  - `frontend/src/utils/error-boundary.ts` (新增)
  - `frontend/src/main.ts` (调用新模块)
  - `frontend/src/views/workorder/Detail.vue` (catch + el-empty)
  - `tests/unit/utils/error-boundary.spec.ts` (新增 14 用例)

---

## 后续 TODO(留给后续 sprint)

1. 25 个 view 的 `load()` 函数改 `try/finally` → `try/catch/finally`,显式处理每个 API 异常(降级数据 / 错误提示 / 重试按钮)
2. 给每个 view 写组件测试,验证 API 失败时的渲染兜底
3. 抽取公共 `useApiList()` / `useApiDetail()` composable,统一错误处理模式

---

## 追加 — Bug #3: 后端未注册 API 触发"系统异常"红字

**报告时间**: 2026-06-28 第二次巡检

**症状**: 用户反馈系统弹出两条"系统异常"提示:

```
系统异常: No static resource workorder/stats.
系统异常: No static resource monitor/topology/graph.
```

**根因分析**:

1. **后端契约差距** — 列出后端全部 15 个 `@RequestMapping` 根路径(`alert`/`auth`/`dashboard`/`data`/`iot-console`/`iot/device`/`iot/device-group`/`iot/device-shadow`/`iot/product`/`rule`/`system/menu|role|user|user-role`/`actuator`/`iot-console` + Spring 默认兜底),**没有 workorder 模块,没有 monitor 模块**。
2. **前端调用** — dashboard `loadAll` 调 `getWorkOrderStats()` 和 `getTopologyGraph()`,两个 API 后端不存在。
3. **Spring Boot 兜底** — 路径未匹配任何 Controller,Spring 转发到 static resource handler;静态资源也没有,抛 `NoResourceFoundException`,响应 404 + `message = "No static resource workorder/stats."`
4. **前端拦截器** — `request.ts` 的 error interceptor **总是** `ElMessage.error(msg)`,即便业务层 `.catch(() => ({ data: ... }))` 已兜底数据,拦截器仍把这条错误消息弹出成"系统异常"红字。

### 修复 #3 — request.ts 拦截器识别 No static resource 静默

**修改**: `frontend/src/api/request.ts`

- 提取 `isSpringNoStaticResourceError(error, derivedMsg?)` 纯函数(导出便于单测)
- error interceptor 调用该函数,匹配时**不弹 ElMessage.error**,仅 `Promise.reject(error)` 让业务 catch 接管
- 其它 4xx/5xx 行为不变(仍弹 ElMessage)

**新增测试**: `tests/unit/api/request.spec.ts` — 10 个用例覆盖:

- `error.response.data.message` 以 "No static resource" 开头 → true
- `error.message` 含 "No static resource"(无 response)→ true
- derivedMsg 参数优先于 error.message
- 普通 Error / 其它 404 / null / undefined → false
- 空对象 / 空 derivedMsg → false

### 验证

- `isSpringNoStaticResourceError` 单测: **10/10 通过**
- 全部测试: **63/63 通过**(新增 10 + 原有 53)
- `vue-tsc --noEmit`: **0 错误**
- 真实端到端验证未做(后端用户凭证未公开;通过单元测试 + 代码 review 覆盖)

### 后端契约差距(留给后续 sprint)

后端缺失 13+ 个前端已实现的 view 对应模块:

- `workorder` — 工单管理(7 个 API:`/stats`、`/page`、`/:id`、`/:id/logs`、`/:id/assign`、`/:id/complete` 等)
- `monitor` — 监测中心(pd / prpd / temperature / environment / gis / topology 共 ~25 个 API)
- `knowledge` — 知识库
- `ops/statistics` — 运维统计
- `report/center` — 报表中心
- `system/{tenant, organization, dict, log, notify}` — 系统子模块

每个模块的补全是独立的 backend sprint。当前前端通过 `.catch` + 全局兜底机制保证可用性,但页面展示的是空数据 / mock 拓扑,不是真实数据。