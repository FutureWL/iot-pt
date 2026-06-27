# 主题支持(浅色/暗色/跟随系统) 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `MainLayout.vue` 中 60 行散乱的主题切换逻辑抽取为 Pinia store,补齐 Element Plus 暗色 CSS,消除首屏 FOUC 闪烁,配套单元测试。

**Architecture:** 单一 Pinia setup store (`useThemeStore`) 持有 `mode` / `resolved` 状态,封装 `localStorage` 读写与 `matchMedia` 监听。`index.html` 注入 12 行内联脚本在 CSS 加载前把 `dark` class 挂到 `<html>`,从源头消除 FOUC。`element-overrides.scss` 补 `html.dark` 块使 Element Plus 组件同步变色。

**Tech Stack:** Vue 3.5 + Vite 5 + TypeScript 5.6 + Pinia 2.2 + Vitest 1.6 (happy-dom) + Element Plus 2.8

---

## Global Constraints

来自 spec `docs/superpowers/specs/2026-06-28-theme-support-design.md`:
- 路径别名: `@/*` → `frontend/src/*` (tsconfig + vite alias)
- 测试环境: Vitest + happy-dom,全局 API 可用,`tests/setup.ts` 自动清理 mock
- 测试入口: `src/**/*.{test,spec}.{ts,tsx}` 与 `tests/unit/**/*.{test,spec}.{ts,tsx}`
- 单元测试断言目标: `document.documentElement.classList.contains('dark')` 与 `localStorage.getItem('iot-theme')`
- TypeScript 严格模式 (`strict: true`),`noEmit: true` — 不编译产物
- Pinia 必须用 setup store 模式 (`ref` / `computed` / `function`),返回字段须通过 `storeToRefs` 解构
- 提交规范: `<type>(scope): <desc>`,格式严格遵循 git-workflow.md
- 不引入新依赖(零 npm install)
- 不改 main.ts 现有 `app.use(router)` / `app.use(ElementPlus)` 顺序,仅在 `mount` 前后插入主题相关调用

---

## File Structure

| 文件 | 职责 |
|---|---|
| `frontend/src/stores/theme.ts` | 🆕 Pinia setup store:`mode` / `resolved` / `init` / `watchSystem` / `setMode` |
| `frontend/src/stores/__tests__/theme.test.ts` | 🆕 8 个 Vitest 用例,覆盖三种模式 + 持久化 + 系统监听 + 错误兜底 |
| `frontend/src/styles/element-overrides.scss` | ✏️ 追加 `html.dark` 块 |
| `frontend/src/main.ts` | ✏️ `useThemeStore().init()` 在 mount 前,`watchSystem()` 在 mount 后 |
| `frontend/index.html` | ✏️ `<head>` 注入 FOUC 拦截内联脚本 |
| `frontend/src/layouts/MainLayout.vue` | ✏️ 删除 60 行手写逻辑,改用 `storeToRefs` 消费 store |

---

## Task 1: 创建 useThemeStore (基础状态与持久化)

**Files:**
- Create: `frontend/src/stores/theme.ts`
- Test: `frontend/src/stores/__tests__/theme.test.ts`

**Interfaces:**
- Produces:
  - `export type ThemeMode = 'light' | 'dark' | 'system'`
  - `export type ResolvedTheme = 'light' | 'dark'`
  - `export const useThemeStore = defineStore('theme', () => { ... })`
  - 返回字段: `mode: Ref<ThemeMode>`, `resolved: Ref<ResolvedTheme>`, `isDark: ComputedRef<boolean>`, `setMode(m: ThemeMode): void`, `init(): void`, `watchSystem(): () => void`
- Consumes: 无 (这是第一个 store,无依赖)

- [ ] **Step 1: 写失败测试 — init() 三种场景**

新建 `frontend/src/stores/__tests__/theme.test.ts`:

```ts
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useThemeStore, type ThemeMode } from '../theme'

// 内存版 localStorage
const storage = new Map<string, string>()
const fakeStorage = {
  getItem: (k: string) => storage.get(k) ?? null,
  setItem: (k: string, v: string) => { storage.set(k, v) },
  removeItem: (k: string) => { storage.delete(k) },
  clear: () => { storage.clear() },
  key: () => null,
  length: 0
}

// 简易 matchMedia mock,允许测试中动态切换
type Listener = (e: { matches: boolean }) => void
let currentMatch = false
const listeners: Listener[] = []
const fakeMq: MediaQueryList = {
  matches: currentMatch,
  media: '(prefers-color-scheme: dark)',
  onchange: null,
  addEventListener: (_: string, l: Listener) => { listeners.push(l) },
  removeEventListener: (_: string, l: Listener) => {
    const i = listeners.indexOf(l)
    if (i >= 0) listeners.splice(i, 1)
  },
  addListener: (l: Listener) => listeners.push(l),
  removeListener: (l: Listener) => {
    const i = listeners.indexOf(l)
    if (i >= 0) listeners.splice(i, 1)
  },
  dispatchEvent: () => true
} as unknown as MediaQueryList

beforeEach(() => {
  vi.stubGlobal('localStorage', fakeStorage)
  vi.stubGlobal('matchMedia', () => fakeMq)
  storage.clear()
  document.documentElement.classList.remove('dark')
  currentMatch = false
  listeners.length = 0
  setActivePinia(createPinia())
})

describe('useThemeStore — init()', () => {
  it('空 localStorage 时,mode = system,resolved 跟随 matchMedia(false)', () => {
    currentMatch = false
    const store = useThemeStore()
    store.init()
    expect(store.mode).toBe('system')
    expect(store.resolved).toBe('light')
    expect(store.isDark).toBe(false)
    expect(document.documentElement.classList.contains('dark')).toBe(false)
  })

  it('空 localStorage 时,matchMedia 偏好暗色,resolved = dark,html.dark class 挂上', () => {
    currentMatch = true
    const store = useThemeStore()
    store.init()
    expect(store.resolved).toBe('dark')
    expect(document.documentElement.classList.contains('dark')).toBe(true)
  })

  it('localStorage 有合法 dark 值,mode = dark,resolved = dark', () => {
    storage.set('iot-theme', 'dark')
    const store = useThemeStore()
    store.init()
    expect(store.mode).toBe('dark')
    expect(store.resolved).toBe('dark')
    expect(document.documentElement.classList.contains('dark')).toBe(true)
  })

  it('localStorage 有合法 light 值,mode = light,html.dark class 移除', () => {
    storage.set('iot-theme', 'light')
    document.documentElement.classList.add('dark')
    const store = useThemeStore()
    store.init()
    expect(store.mode).toBe('light')
    expect(store.resolved).toBe('light')
    expect(document.documentElement.classList.contains('dark')).toBe(false)
  })

  it('localStorage 有非法值(比如 "rainbow"),兜底为 system', () => {
    storage.set('iot-theme', 'rainbow')
    const store = useThemeStore()
    store.init()
    expect(store.mode).toBe('system')
  })
})
```

- [ ] **Step 2: 运行测试,确认全部失败**

Run:
```bash
cd /home/weilai/CodeProject/iot-pt/frontend && npx vitest run src/stores/__tests__/theme.test.ts
```

Expected: 失败,`Cannot find module '../theme'` 或类似 import 错误。

- [ ] **Step 3: 实现 useThemeStore (仅 init / setMode / isDark)**

新建 `frontend/src/stores/theme.ts`:

```ts
import { defineStore } from 'pinia'
import { ref, computed, type Ref, type ComputedRef } from 'vue'

export type ThemeMode = 'light' | 'dark' | 'system'
export type ResolvedTheme = 'light' | 'dark'

const STORAGE_KEY = 'iot-theme'
const HTML_CLASS = 'dark'
const MEDIA_QUERY = '(prefers-color-scheme: dark)'

function readModeFromStorage(): ThemeMode {
  let raw: string | null = null
  try {
    raw = localStorage.getItem(STORAGE_KEY)
  } catch {
    return 'system'
  }
  return raw === 'light' || raw === 'dark' || raw === 'system' ? raw : 'system'
}

function writeModeToStorage(mode: ThemeMode): void {
  try {
    localStorage.setItem(STORAGE_KEY, mode)
  } catch {
    /* quota / privacy mode — ignore */
  }
}

function readSystemPrefers(): ResolvedTheme {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return 'light'
  }
  return window.matchMedia(MEDIA_QUERY).matches ? 'dark' : 'light'
}

function resolveFromMode(mode: ThemeMode): ResolvedTheme {
  return mode === 'system' ? readSystemPrefers() : mode
}

function applyClass(resolved: ResolvedTheme): void {
  document.documentElement.classList.toggle(HTML_CLASS, resolved === 'dark')
}

export const useThemeStore = defineStore('theme', () => {
  const mode: Ref<ThemeMode> = ref<ThemeMode>('system')
  const resolved: Ref<ResolvedTheme> = ref<ResolvedTheme>('light')

  const isDark: ComputedRef<boolean> = computed(() => resolved.value === 'dark')

  function setMode(m: ThemeMode): void {
    mode.value = m
    resolved.value = resolveFromMode(m)
    applyClass(resolved.value)
    writeModeToStorage(m)
  }

  function init(): void {
    mode.value = readModeFromStorage()
    resolved.value = resolveFromMode(mode.value)
    applyClass(resolved.value)
  }

  return { mode, resolved, isDark, setMode, init }
})
```

- [ ] **Step 4: 运行测试,确认 5 个 init 用例全通过**

Run:
```bash
cd /home/weilai/CodeProject/iot-pt/frontend && npx vitest run src/stores/__tests__/theme.test.ts
```

Expected: 5 passed, 0 failed。

- [ ] **Step 5: 提交**

```bash
cd /home/weilai/CodeProject/iot-pt && git add frontend/src/stores/theme.ts frontend/src/stores/__tests__/theme.test.ts && git commit -m "feat(theme): 新建 useThemeStore 基础状态(init/setMode/isDark)

- 5 个 init 用例通过(空 storage / 合法值 / 非法值兜底 / matchMedia 暗色偏好)
- 零依赖,纯 ref/computed/function setup store
- localStorage 读写失败时静默兜底,符合 4.4 错误处理约定

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 2: 补 watchSystem() 与 setMode 错误兜底测试

**Files:**
- Modify: `frontend/src/stores/__tests__/theme.test.ts` (追加 watchSystem 用例)
- Modify: `frontend/src/stores/theme.ts` (实现 watchSystem)

**Interfaces:**
- Consumes: Task 1 的 `useThemeStore` 与 fakeStorage / fakeMq mock
- Produces: 新增 `watchSystem(): () => void` action(返回 stop 函数)

- [ ] **Step 1: 写失败测试 — watchSystem 行为**

在 `frontend/src/stores/__tests__/theme.test.ts` 末尾追加:

```ts
describe('useThemeStore — setMode()', () => {
  it('setMode("light") 后,class 移除 + localStorage 写入', () => {
    document.documentElement.classList.add('dark')
    const store = useThemeStore()
    store.setMode('light')
    expect(store.mode).toBe('light')
    expect(store.resolved).toBe('light')
    expect(document.documentElement.classList.contains('dark')).toBe(false)
    expect(storage.get('iot-theme')).toBe('light')
  })

  it('setMode("dark") 后,class 挂上 + localStorage 写入', () => {
    const store = useThemeStore()
    store.setMode('dark')
    expect(store.mode).toBe('dark')
    expect(store.resolved).toBe('dark')
    expect(document.documentElement.classList.contains('dark')).toBe(true)
    expect(storage.get('iot-theme')).toBe('dark')
  })

  it('setMode("system") 后,resolved 跟随 matchMedia', () => {
    currentMatch = true
    const store = useThemeStore()
    store.setMode('system')
    expect(store.resolved).toBe('dark')
    expect(document.documentElement.classList.contains('dark')).toBe(true)
  })

  it('localStorage 写入抛错时,mode 仍更新,不抛', () => {
    const setItem = vi.fn(() => { throw new Error('quota') })
    vi.stubGlobal('localStorage', { ...fakeStorage, setItem })
    const store = useThemeStore()
    expect(() => store.setMode('dark')).not.toThrow()
    expect(store.mode).toBe('dark')
  })
})

describe('useThemeStore — watchSystem()', () => {
  it('mode = system 时,系统切到暗色,resolved 跟着变 + class 挂上', () => {
    currentMatch = false
    const store = useThemeStore()
    store.setMode('system')
    expect(store.resolved).toBe('light')

    // 模拟系统主题变化
    currentMatch = true
    listeners.forEach(l => l({ matches: true }))

    expect(store.resolved).toBe('dark')
    expect(document.documentElement.classList.contains('dark')).toBe(true)
  })

  it('mode = system 时,系统切到亮色,resolved 跟着变 + class 移除', () => {
    currentMatch = true
    const store = useThemeStore()
    store.setMode('system')
    expect(store.resolved).toBe('dark')

    currentMatch = false
    listeners.forEach(l => l({ matches: false }))

    expect(store.resolved).toBe('light')
    expect(document.documentElement.classList.contains('dark')).toBe(false)
  })

  it('mode = light 时,系统切到暗色,resolved 不响应', () => {
    currentMatch = false
    const store = useThemeStore()
    store.setMode('light')

    currentMatch = true
    listeners.forEach(l => l({ matches: true }))

    expect(store.resolved).toBe('light')
    expect(document.documentElement.classList.contains('dark')).toBe(false)
  })

  it('mode = dark 时,系统切到亮色,resolved 不响应', () => {
    currentMatch = true
    const store = useThemeStore()
    store.setMode('dark')

    currentMatch = false
    listeners.forEach(l => l({ matches: false }))

    expect(store.resolved).toBe('dark')
    expect(document.documentElement.classList.contains('dark')).toBe(true)
  })

  it('stop() 调用后,监听器解绑,系统变化不再触发', () => {
    const store = useThemeStore()
    store.setMode('system')
    const stop = store.watchSystem()
    expect(listeners.length).toBe(1)
    stop()
    expect(listeners.length).toBe(0)

    currentMatch = true
    listeners.forEach(l => l({ matches: true }))  // 应无 listener
    expect(store.resolved).toBe('light')  // 仍保持
  })
})
```

- [ ] **Step 2: 运行测试,确认新加的 9 个用例失败(因 watchSystem 未实现)**

Run:
```bash
cd /home/weilai/CodeProject/iot-pt/frontend && npx vitest run src/stores/__tests__/theme.test.ts
```

Expected: 5 passed, 9 failed(新加的用例因 `store.watchSystem is not a function` 失败)。

- [ ] **Step 3: 在 store 中实现 watchSystem()**

编辑 `frontend/src/stores/theme.ts`,在 `init` 函数之后追加:

```ts
  function watchSystem(): () => void {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
      return () => {}
    }
    const mq = window.matchMedia(MEDIA_QUERY)
    const handler = (e: MediaQueryListEvent | { matches: boolean }) => {
      if (mode.value !== 'system') return
      const next: ResolvedTheme = e.matches ? 'dark' : 'light'
      if (resolved.value === next) return
      resolved.value = next
      applyClass(next)
    }
    if (mq.addEventListener) {
      mq.addEventListener('change', handler)
    } else if (mq.addListener) {
      mq.addListener(handler)  // Safari < 14 兼容
    }
    return () => {
      if (mq.removeEventListener) {
        mq.removeEventListener('change', handler)
      } else if (mq.removeListener) {
        mq.removeListener(handler)
      }
    }
  }
```

并在 `return { ... }` 中加入 `watchSystem`:

```ts
  return { mode, resolved, isDark, setMode, init, watchSystem }
```

- [ ] **Step 4: 运行测试,确认 14 个用例全通过**

Run:
```bash
cd /home/weilai/CodeProject/iot-pt/frontend && npx vitest run src/stores/__tests__/theme.test.ts
```

Expected: 14 passed, 0 failed。

- [ ] **Step 5: 运行 type-check 确认无 TS 错误**

Run:
```bash
cd /home/weilai/CodeProject/iot-pt/frontend && npx vue-tsc --noEmit
```

Expected: 0 errors。

- [ ] **Step 6: 提交**

```bash
cd /home/weilai/CodeProject/iot-pt && git add frontend/src/stores/theme.ts frontend/src/stores/__tests__/theme.test.ts && git commit -m "feat(theme): watchSystem() 系统主题监听 + setMode 错误兜底

- 新增 9 个测试: setMode 三模式 + 写入抛错兜底;watchSystem 双模式响应 + stop 解绑
- 兼容 Safari < 14 的 addListener/removeListener API
- watchSystem 返回 stop 函数供调用方清理

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 3: 补 Element Plus 暗色 CSS 变量

**Files:**
- Modify: `frontend/src/styles/element-overrides.scss:1-135` (在文件末尾追加 `html.dark` 块)

- [ ] **Step 1: 在 element-overrides.scss 末尾追加 `html.dark` 块**

编辑 `frontend/src/styles/element-overrides.scss`,在文件末尾(第 135 行 `}` 之后,加一个新行)追加:

```scss
// ============================================================
// 暗色模式 — html.dark 激活
// ============================================================
// 当 <html> 挂上 .dark class 时,所有 --el-* 变量重新赋值,
// 使 Element Plus 组件(按钮 / 输入框 / 表格 / 对话框 等)自动适配暗色。
//
// 数值与 styles/css-vars.scss 的 html.dark 块保持一致:
//   --el-color-primary   = #5ba6ff   (= --iot-color-primary)
//   --el-bg-color        = #1e293b   (= --iot-bg-card)
//   --el-bg-color-page   = #0f172a   (= --iot-bg-page)
//   --el-text-color-primary = #e2e8f0 (= --iot-text-primary)
//   --el-border-color    = #334155   (= --iot-border-base)
// ============================================================

html.dark {
  // ---- 主色 ----
  --el-color-primary: #5ba6ff;
  --el-color-primary-light-3: #7ab8ff;
  --el-color-primary-light-5: #9cc9ff;
  --el-color-primary-light-7: #c0dfff;
  --el-color-primary-light-8: #d8ebff;
  --el-color-primary-light-9: #1e3a5c;
  --el-color-primary-dark-2: #409eff;

  // ---- 语义色 ----
  --el-color-success: #85ce61;
  --el-color-success-light-3: #2d4a35;
  --el-color-success-light-5: #2d4a35;
  --el-color-success-light-7: #2d4a35;
  --el-color-success-light-8: #2d4a35;
  --el-color-success-light-9: #2d4a35;
  --el-color-success-dark-2: #5daf34;

  --el-color-warning: #eebe77;
  --el-color-warning-light-3: #4a3a25;
  --el-color-warning-light-5: #4a3a25;
  --el-color-warning-light-7: #4a3a25;
  --el-color-warning-light-8: #4a3a25;
  --el-color-warning-light-9: #4a3a25;
  --el-color-warning-dark-2: #c89c5a;

  --el-color-danger: #f78989;
  --el-color-danger-light-3: #4a2d2d;
  --el-color-danger-light-5: #4a2d2d;
  --el-color-danger-light-7: #4a2d2d;
  --el-color-danger-light-8: #4a2d2d;
  --el-color-danger-light-9: #4a2d2d;
  --el-color-danger-dark-2: #d96c6c;

  --el-color-error: #f78989;
  --el-color-error-light-3: #4a2d2d;
  --el-color-error-light-5: #4a2d2d;
  --el-color-error-light-7: #4a2d2d;
  --el-color-error-light-8: #4a2d2d;
  --el-color-error-light-9: #4a2d2d;
  --el-color-error-dark-2: #d96c6c;

  --el-color-info: #a6a9ad;
  --el-color-info-light-3: #2e3236;
  --el-color-info-light-5: #2e3236;
  --el-color-info-light-7: #2e3236;
  --el-color-info-light-8: #2e3236;
  --el-color-info-light-9: #2e3236;
  --el-color-info-dark-2: #82858a;

  // ---- 文字 ----
  --el-text-color-primary: #e2e8f0;
  --el-text-color-regular: #cbd5e1;
  --el-text-color-secondary: #94a3b8;
  --el-text-color-placeholder: #64748b;
  --el-text-color-disabled: #475569;

  // ---- 边框 ----
  --el-border-color: #334155;
  --el-border-color-light: #475569;
  --el-border-color-lighter: #1e293b;
  --el-border-color-extra-light: #0f172a;
  --el-border-color-darker: #1e293b;
  --el-border-color-dark: #2a3f5a;

  // ---- 背景 ----
  --el-bg-color: #1e293b;
  --el-bg-color-page: #0f172a;
  --el-bg-color-overlay: #1e293b;

  // ---- 表格 ----
  .el-table {
    --el-table-row-hover-bg-color: #334155;
    --el-table-border-color: #334155;
    --el-table-header-bg-color: #1e293b;
    --el-table-header-text-color: #e2e8f0;
  }
}
```

- [ ] **Step 2: 启动 dev server,手动验证 CSS 解析无错误**

Run:
```bash
cd /home/weilai/CodeProject/iot-pt/frontend && timeout 8 npx vite build --logLevel info 2>&1 | tail -30
```

Expected: 无 SCSS 编译错误。如果有 `Undefined variable` 等,检查是否漏写 `@use`。

- [ ] **Step 3: 提交**

```bash
cd /home/weilai/CodeProject/iot-pt && git add frontend/src/styles/element-overrides.scss && git commit -m "feat(theme): element-overrides.scss 追加 html.dark 块

所有 --el-* 变量在暗色下重新赋值,使 Element Plus 组件
(按钮/输入/表格/对话框等)跟随 <html>.dark 切换同步变色。
数值与 css-vars.scss 的 html.dark 块保持一致。

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 4: index.html 注入 FOUC 拦截脚本

**Files:**
- Modify: `frontend/index.html:1-26`

- [ ] **Step 1: 在 `<head>` 中注入内联脚本**

编辑 `frontend/index.html`,在 `<title>物联网平台</title>` 之后,`</head>` 之前插入:

```html
    <script>
      // 主题预检测 — 在 CSS 加载前把 .dark class 挂到 <html>
      // 防止首屏"亮→暗"闪烁(FOUC)
      (function () {
        try {
          var m = localStorage.getItem('iot-theme');
          var dark = m === 'dark'
            || (m !== 'light' && window.matchMedia('(prefers-color-scheme: dark)').matches);
          document.documentElement.classList.toggle('dark', dark);
        } catch (e) { /* localStorage 不可用则保持 light */ }
      })();
    </script>
```

完整文件应为:

```html
<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <link rel="icon" type="image/svg+xml" href="/favicon.svg" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
    <meta name="theme-color" content="#409eff" />
    <title>物联网平台</title>
    <script>
      // 主题预检测 — 在 CSS 加载前把 .dark class 挂到 <html>
      // 防止首屏"亮→暗"闪烁(FOUC)
      (function () {
        try {
          var m = localStorage.getItem('iot-theme');
          var dark = m === 'dark'
            || (m !== 'light' && window.matchMedia('(prefers-color-scheme: dark)').matches);
          document.documentElement.classList.toggle('dark', dark);
        } catch (e) { /* localStorage 不可用则保持 light */ }
      })();
    </script>
  </head>
  <body>
    <div id="app">
      <div class="app-loading">
        <div class="app-loading-spinner"></div>
        <div class="app-loading-text">物联网平台加载中...</div>
      </div>
    </div>
    <style>
      .app-loading { position: fixed; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; background: #f5f7fa; }
      .app-loading-spinner { width: 48px; height: 48px; border: 4px solid #e4e7ed; border-top-color: #409eff; border-radius: 50%; animation: app-loading-spin 0.8s linear infinite; }
      .app-loading-text { margin-top: 16px; color: #606266; font-size: 14px; }
      @keyframes app-loading-spin { to { transform: rotate(360deg); } }
    </style>
    <script type="module" src="/src/main.ts"></script>
  </body>
</html>
```

- [ ] **Step 2: 提交**

```bash
cd /home/weilai/CodeProject/iot-pt && git add frontend/index.html && git commit -m "feat(theme): index.html 注入 FOUC 拦截内联脚本

在 CSS 加载前同步读取 localStorage + matchMedia,
把 .dark class 挂到 <html>,避免首屏"亮→暗"闪烁。
脚本仅 ~10 行,执行 < 1ms,不影响首屏性能。

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 5: main.ts 接入 store

**Files:**
- Modify: `frontend/src/main.ts:1-27`

- [ ] **Step 1: 编辑 main.ts,在 `app.mount` 前后插入主题相关调用**

将 `frontend/src/main.ts` 替换为:

```ts
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
// @ts-ignore
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
// 加载 Element Plus 基础样式(ElMessage / ElNotification / ElMessageBox / ElLoading
// 等以命令式 API 调用的组件不在模板中,unplugin-vue-components 不会为它们注入 CSS,
// 这里显式补齐,否则提示/通知会因缺 position:fixed 等样式而看不见)
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import './styles/index.scss'
import { useThemeStore } from './stores/theme'

const app = createApp(App)

app.use(createPinia())

// 注册所有 Element Plus 图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

// 主题初始化(必须在 mount 之前 — 让响应式状态就绪,与 index.html 内联脚本保持一致)
const themeStore = useThemeStore()
themeStore.init()

app.use(router)
app.use(ElementPlus, { locale: zhCn, size: 'default' })
app.mount('#app')

// 启动系统主题监听(mount 之后 — 仅响应 system 模式的系统变化)
themeStore.watchSystem()
```

- [ ] **Step 2: 运行 type-check**

Run:
```bash
cd /home/weilai/CodeProject/iot-pt/frontend && npx vue-tsc --noEmit
```

Expected: 0 errors。

- [ ] **Step 3: 提交**

```bash
cd /home/weilai/CodeProject/iot-pt && git add frontend/src/main.ts && git commit -m "feat(theme): main.ts 接入 useThemeStore

- createPinia() 之后立即 themeStore.init()(读 storage + 应用 class)
- app.mount() 之后调用 watchSystem()(注册 matchMedia 监听)
- 顺序严格: init 必须在 mount 之前,watchSystem 在 mount 之后
  (与 index.html 内联脚本 + 文档契约 §6.2 对齐)

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 6: MainLayout.vue 重构为消费 store

**Files:**
- Modify: `frontend/src/layouts/MainLayout.vue:1-60` (删除 60 行手写主题逻辑)
- Modify: `frontend/src/layouts/MainLayout.vue:338-365` (模板中绑定方式调整)

- [ ] **Step 1: 替换 `<script setup>` 顶部的 import 与主题逻辑块**

将 `frontend/src/layouts/MainLayout.vue` 第 1-7 行的 import 替换为:

```ts
<script setup lang="ts">
import { computed, ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useUserStore } from '@/stores/user'
import { useThemeStore } from '@/stores/theme'
import {
  UserFilled, Fold, Expand, Sunny, MoonNight, Monitor, Check
} from '@element-plus/icons-vue'
```

将第 33-59 行(原主题逻辑块,含 `type ThemeMode = ...` 到 `function onSystemThemeChange() {...}` 结束)**整段删除**,替换为:

```ts
// ============== 主题(由 useThemeStore 统一管理) ==============
const themeStore = useThemeStore()
const { mode, resolved } = storeToRefs(themeStore)

function onThemeChange(cmd: 'light' | 'dark' | 'system') {
  themeStore.setMode(cmd)
}
```

> 注意: 第 203-209 行的 `onMounted` 中 `applyTheme(theme.value)` 和 `mediaQuery.addEventListener(...)` 需要移除,因为这部分职责已转移到 `themeStore.init()` / `watchSystem()`,由 `main.ts` 在应用入口统一调用。第 211-215 行的 `onBeforeUnmount` 中 `mediaQuery.removeEventListener` 同样移除。

将第 202-215 行的 `// ============== 生命周期 ==============` 块替换为:

```ts
// ============== 生命周期 ==============
onMounted(() => {
  // 主题初始化与系统监听由 main.ts 在应用入口统一管理
  // 此处仅保留布局相关副作用(如有)
})
```

- [ ] **Step 2: 模板调整 — `theme` 改为 `mode`,`effectiveTheme` 改为 `resolved`**

在第 339-365 行(主题下拉菜单部分),将:

- `theme === 'system' ? '跟随系统' : ...` 改为 `mode === 'system' ? '跟随系统' : ...`
- `theme === 'light' ? 'Sunny' : theme === 'dark' ? 'MoonNight' : 'Monitor'` 改为 `mode === 'light' ? 'Sunny' : mode === 'dark' ? 'MoonNight' : 'Monitor'`
- 所有 `theme === 'light'` / `theme === 'dark'` / `theme === 'system'` 高亮判断改为 `mode === 'light'` / `mode === 'dark'` / `mode === 'system'`
- 删除 `effectiveTheme` 引用(原代码未直接使用,删除即可)

- [ ] **Step 3: 运行 type-check**

Run:
```bash
cd /home/weilai/CodeProject/iot-pt/frontend && npx vue-tsc --noEmit
```

Expected: 0 errors。

- [ ] **Step 4: 运行单元测试,确认 14 个用例仍全通过**

Run:
```bash
cd /home/weilai/CodeProject/iot-pt/frontend && npx vitest run src/stores/__tests__/theme.test.ts
```

Expected: 14 passed, 0 failed。

- [ ] **Step 5: 提交**

```bash
cd /home/weilai/CodeProject/iot-pt && git add frontend/src/layouts/MainLayout.vue && git commit -m "refactor(layout): MainLayout 消费 useThemeStore,删除 60 行内联主题逻辑

- 主题切换、localStorage 持久化、matchMedia 监听
  全部委托给 useThemeStore,MainLayout 仅负责 UI 渲染
- 主题相关生命周期调用由 main.ts 统一管理
  (此处 onMounted/onBeforeUnmount 留空,等布局相关副作用时填充)
- 模板中 theme/effectiveTheme 引用统一替换为 storeToRefs 解构的 mode/resolved

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 7: 端到端验证

**Files:**
- Verify: 上述所有文件已在 dev server / 测试套件中工作

- [ ] **Step 1: 运行完整单元测试**

Run:
```bash
cd /home/weilai/CodeProject/iot-pt/frontend && npm run test:run
```

Expected: 全部 passed (包括 store/theme 的 14 个 + 项目原有测试)。

- [ ] **Step 2: 运行 type-check**

Run:
```bash
cd /home/weilai/CodeProject/iot-pt/frontend && npm run type-check
```

Expected: 0 errors。

- [ ] **Step 3: 启动 dev server,手动验收**

Run:
```bash
cd /home/weilai/CodeProject/iot-pt/frontend && npm run dev
```

在浏览器 (http://localhost:33411) 中手动验证验收清单 `docs/superpowers/specs/2026-06-28-theme-support-design.md` §10:

- [ ] DevTools → Application → Local Storage → 修改 `iot-theme = 'dark'`,刷新页面,首屏就是暗色(无闪烁)
- [ ] 顶栏点击主题下拉 → 选「暗色」,整个页面(含 Element Plus 组件)立即变暗
- [ ] 选「浅色」→ 立即变亮
- [ ] 选「跟随系统」,把系统切到暗色 → 页面跟着变暗
- [ ] 关闭页面再打开,主题保持上次选择
- [ ] DevTools Console 无 warning/error

- [ ] **Step 4: 提交验证记录(可选)**

如果手动验收有发现需要修复的细节,创建修复提交。无需新增"验证通过"提交。

---

## Self-Review

按 writing-plans skill 要求:

**1. Spec coverage**(逐条对照 `docs/superpowers/specs/2026-06-28-theme-support-design.md`):

- §3 数据契约: Task 1 + Task 2 完整实现 ✓
- §4 行为契约 (init/watchSystem/setMode/错误处理): Task 1 覆盖 init+setMode,Task 2 覆盖 watchSystem + 错误兜底 ✓
- §5 样式契约: Task 3 完整 ✓
- §6 启动时序: Task 4 (index.html) + Task 5 (main.ts) 严格按顺序 ✓
- §7 消费侧: Task 6 完整 ✓
- §8 测试: Task 1 5 个 + Task 2 9 个 = 14 个用例,覆盖 §8.1 表格全部 8 条 ✓
- §9 文件清单: 2 新文件 + 4 改,与本计划一致 ✓
- §10 验收清单: Task 7 Step 3 逐条勾选 ✓
- §11 风险: Task 5 Step 1 注释明确"init 在 mount 前,watchSystem 在 mount 后"对应"Pinia 初始化时机搞错"风险 ✓

**2. Placeholder scan**:
- 无 "TBD" / "TODO" / "implement later"
- 无 "add appropriate error handling" — 错误处理在 Task 2 步骤 1 + 步骤 3 显式实现
- 无 "similar to Task N" — 每个任务的代码块独立完整
- 所有"修改代码"步骤都有完整代码块
- 所有引用类型/函数在前面任务中已定义

**3. Type consistency**:
- `useThemeStore` 在 Task 1 定义,Task 2 扩展,Task 5 消费,Task 6 消费 — 一致
- `ThemeMode` / `ResolvedTheme` 类型在 Task 1 导出,Task 2/5/6 复用 — 一致
- `setMode` / `init` / `watchSystem` 签名在 Task 1 定义,Task 2 扩展,Task 5 验证 — 一致
- `STORAGE_KEY = 'iot-theme'` 在 store 与 index.html 中字符串一致 ✓

无 issue,plan 落地。
