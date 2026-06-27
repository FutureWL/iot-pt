# 主题支持(浅色 / 暗色 / 跟随系统) 设计文档

- **日期**: 2026-06-28
- **范围**: iot-pt 前端 (`frontend/`)
- **状态**: 待用户审阅
- **目标读者**: 实施该功能的工程师

---

## 1. 背景与目标

### 1.1 现状

`MainLayout.vue` 中**已经存在**一套主题切换 UI 和逻辑:

- 顶栏右侧的 `el-dropdown` 提供「浅色 / 暗色 / 跟随系统」三选项
- `localStorage` 键 `iot-theme` 持久化用户选择
- `matchMedia('(prefers-color-scheme: dark)')` 监听系统变化
- `document.documentElement.classList.toggle('dark', ...)` 切换 CSS

CSS 基建**已经存在**:

- `styles/css-vars.scss` 中 `html.dark` 块定义完整的 iot-* 暗色变量
- `styles/index.scss` 已 `@use 'element-plus/theme-chalk/dark/css-vars.css'`

### 1.2 缺口

| 缺口 | 影响 |
|---|---|
| `element-overrides.scss` 中 `--el-*` 变量**只**定义在 `:root` | 切换暗色时,Element Plus 组件(`el-button` / `el-input` / `el-table` 等)颜色不变 |
| 主题逻辑 60+ 行散落在 `MainLayout.vue` | 难以在登录页、大屏等其它入口复用 |
| `applyTheme` 在 `onMounted` 中执行 | 首次进入页面有「亮→暗」闪烁 (FOUC) |
| 无单元测试 | 切换逻辑改动无回归保护 |

### 1.3 目标

1. **功能正确**: 切换三种模式,所有组件(包括 Element Plus)同步变色
2. **零闪烁**: 首次加载即应用正确主题
3. **可复用**: 主题状态可在任意组件通过 Pinia 消费
4. **可测试**: 核心逻辑覆盖单元测试

### 1.4 非目标 (YAGNI)

- 多套主题色(蓝/绿/红)
- 主题切换的过渡动画可配置化(用 CSS `transition` 一刀切)
- 用户中心「主题偏好」接口(走 localStorage 即可)

---

## 2. 架构

```
┌──────────────────────────────────────────────────────────────┐
│  index.html 内联脚本 (FOUC 拦截)                              │
│    - 读 localStorage['iot-theme']                            │
│    - 立即在 <html> 上挂 .dark class                          │
└──────────────────────────────────────────────────────────────┘
                              ↓
┌──────────────────────────────────────────────────────────────┐
│  main.ts                                                      │
│    createPinia()                                             │
│    useThemeStore().init()    ← mount 前,只读 storage + 应用   │
│    app.mount('#app')                                         │
│    useThemeStore().watchSystem()  ← mount 后,注册 media 监听 │
└──────────────────────────────────────────────────────────────┘
                              ↓
┌──────────────────────────────────────────────────────────────┐
│  Pinia store: useThemeStore (src/stores/theme.ts)            │
│    - mode: 'light' | 'dark' | 'system'                       │
│    - resolved: 'light' | 'dark'  (实际生效值)                  │
│    - 内置 matchMedia 监听管理(setMode / init / watchSystem)   │
│    - 组件不直接接触 matchMedia,只调 store action              │
└──────────────────────────────────────────────────────────────┘
                              ↓
┌──────────────────────────────────────────────────────────────┐
│  消费者 (MainLayout.vue 等)                                   │
│    - 通过 storeToRefs(useThemeStore()) 拿响应式状态           │
│    - 模板渲染 mode 决定下拉菜单高亮,渲染 resolved 决定图标     │
└──────────────────────────────────────────────────────────────┘
```

---

## 3. 数据契约

### 3.1 类型定义 (`src/stores/theme.ts`)

```ts
export type ThemeMode = 'light' | 'dark' | 'system'
export type ResolvedTheme = 'light' | 'dark'

const STORAGE_KEY = 'iot-theme'
const HTML_CLASS = 'dark'
const MEDIA_QUERY = '(prefers-color-scheme: dark)'

/** 持久化值解析:接受合法值,否则兜底为 'system' */
function readModeFromStorage(): ThemeMode {
  const raw = localStorage.getItem(STORAGE_KEY)
  return raw === 'light' || raw === 'dark' || raw === 'system'
    ? raw
    : 'system'
}
```

### 3.2 Store API

```ts
export const useThemeStore = defineStore('theme', () => {
  const mode = ref<ThemeMode>('system')           // 用户选择
  const resolved = ref<ResolvedTheme>('light')    // 实际生效

  // 只读 getter
  const isDark = computed(() => resolved.value === 'dark')

  // 初始化(在 app.mount 之前调用一次)
  function init(): void

  // 启动系统主题监听(在 app.mount 之后调用一次)
  function watchSystem(): () => void   // 返回 stop 函数

  // 用户切换主题
  function setMode(m: ThemeMode): void

  return { mode, resolved, isDark, init, watchSystem, setMode }
})
```

---

## 4. 行为契约

### 4.1 `init()` 时序

1. 从 `localStorage[STORAGE_KEY]` 读取,非法值兜底为 `'system'`
2. 根据 `mode` 计算 `resolved`(`'system'` 时读 `matchMedia`)
3. 把 `resolved === 'dark'` 写到 `<html>.classList`
4. **不**注册 `matchMedia` 监听(由 `watchSystem()` 负责)

### 4.2 `watchSystem()` 时序

1. 获取 `window.matchMedia(MEDIA_QUERY)`
2. 注册 `change` 事件监听
3. 回调: 仅在 `mode === 'system'` 时,根据 `matches` 重新设置 `resolved` + class
4. 返回的 `stop` 函数 `removeEventListener` 解绑

### 4.3 `setMode(m)` 时序

1. 更新 `mode.value = m`
2. 重新计算 `resolved`
3. 同步 `<html>` 的 class
4. 写回 `localStorage[STORAGE_KEY] = m`

### 4.4 错误处理

| 场景 | 行为 |
|---|---|
| `localStorage` 抛错(隐私模式 / SSR) | 降级为 `'system'`,不写入 |
| `matchMedia` 不存在(老浏览器) | `system` 模式降级为 `light` |
| 监听器注册失败 | 控制台 `console.warn` 后继续,不阻塞应用 |

### 4.5 资源清理

`watchSystem()` 的 `stop` 函数必须在 Pinia store **销毁时**调用(目前是 root store,等同应用卸载)。

实现要点: 在 `watchSystem()` 内部维护 `mediaQuery` 引用,提供显式 `dispose()` action(目前不需要,预留)。

---

## 5. 样式契约

### 5.1 `element-overrides.scss` 补充

在文件末尾追加 `html.dark` 块,所有 `--el-*` 变量用暗色 token 重新赋值。

**对照原则**:

- `--el-color-primary` 暗色值: `#5ba6ff`(与 `--iot-color-primary` 暗色值一致)
- `--el-bg-color` 暗色值: `#1e293b` (= `--iot-bg-card`)
- `--el-bg-color-page` 暗色值: `#0f172a` (= `--iot-bg-page`)
- `--el-text-color-primary` 暗色值: `#e2e8f0` (= `--iot-text-primary`)
- `--el-border-color` 暗色值: `#334155` (= `--iot-border-base`)
- 其它衍生变量按相同逻辑映射

### 5.2 过渡动画

`styles/index.scss` 已有 `transition: background-color ..., color ...`,无需新增。

---

## 6. 启动时序

### 6.1 `index.html` 改造

在 `<head>` 中,`<script type="module" src="/src/main.ts">` **之前**,注入:

```html
<script>
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

> **为什么必须**: 这是消除 FOUC 的关键。`<script>` 阻塞解析,但执行只读 localStorage + 切 class,成本 < 1ms,首屏绘制前完成。

### 6.2 `main.ts` 改造

```ts
const app = createApp(App)
app.use(createPinia())

// 主题初始化(必须在 mount 之前,让响应式状态就绪)
const themeStore = useThemeStore()
themeStore.init()

app.use(router)
app.use(ElementPlus, { locale: zhCn, size: 'default' })
app.mount('#app')

// 监听系统主题(必须在 mount 之后,因为依赖 document.body)
themeStore.watchSystem()
```

---

## 7. 消费侧改造

### 7.1 `MainLayout.vue` 改动点

**删除** 第 33-59 行的所有主题逻辑。

**替换为**:

```ts
import { useThemeStore } from '@/stores/theme'
import { storeToRefs } from 'pinia'

const themeStore = useThemeStore()
const { mode, resolved } = storeToRefs(themeStore)
function onThemeChange(cmd: 'light' | 'dark' | 'system') {
  themeStore.setMode(cmd)
}
```

模板中所有 `theme === 'light' ? ...` 改为 `mode === 'light' ? ...`,
`effectiveTheme` 引用改为 `resolved`。

### 7.2 其余组件

登录页 (`views/login/`) 与大屏 (`views/screen/`) **本次不改造** — 它们在原型阶段,等它们开始使用 iot-* 变量时再消费 store 即可。

---

## 8. 测试

### 8.1 单元测试 — `src/stores/__tests__/theme.test.ts`

覆盖:

| 用例 | 期望 |
|---|---|
| `init()` 在空 localStorage 时,`mode === 'system'`,`resolved` 跟随 `matchMedia` | 通过 |
| `init()` 在 `localStorage` 有 `'dark'` 时,`mode === 'dark'`,`resolved === 'dark'`,`html` 有 `dark` class | 通过 |
| `init()` 在 `localStorage` 有非法值时,`mode === 'system'` | 通过 |
| `setMode('light')` 后,`html.dark` class 被移除,`localStorage` 写入 `'light'` | 通过 |
| `setMode('system')` 后,`resolved` 跟随 `matchMedia` | 通过 |
| `watchSystem()` 在 `mode === 'system'` 时,模拟 `matchMedia.change` 事件,`resolved` 切换 | 通过 |
| `watchSystem()` 在 `mode === 'light'` 时,模拟 `change` 事件,**不**响应 | 通过 |
| `setMode` 写入失败(localStorage quota)时,`mode` 已更新,仅持久化失败 → 不抛 | 通过 |

### 8.2 Mock 策略

- `localStorage`: 用 `vi.stubGlobal` 注入内存版
- `matchMedia`: 用 `vi.fn()` mock,手动 `change.matches` 触发
- `<html>` classList: happy-dom 自带,断言 `document.documentElement.classList.contains('dark')`

### 8.3 不写 E2E

Playwright E2E 已存在,本次**不新增**。主题切换会在手动验收 + 单元测试中覆盖。E2E 留给以后做完整 UI 回归时一起加。

---

## 9. 文件清单

| 路径 | 动作 |
|---|---|
| `frontend/src/stores/theme.ts` | 新建 |
| `frontend/src/stores/__tests__/theme.test.ts` | 新建 |
| `frontend/src/styles/element-overrides.scss` | 编辑(追加 `html.dark` 块) |
| `frontend/src/main.ts` | 编辑(增加 store 初始化) |
| `frontend/index.html` | 编辑(增加内联脚本) |
| `frontend/src/layouts/MainLayout.vue` | 编辑(删除旧逻辑,消费 store) |

总计: **2 新文件 + 4 编辑**

---

## 10. 验收清单

- [ ] 浏览器 DevTools 改 `localStorage.iot-theme = 'dark'`,刷新页面,首屏就是暗色(无闪烁)
- [ ] 顶栏点击主题下拉 → 选「暗色」,整个页面(含 Element Plus 组件)立即变暗
- [ ] 选「浅色」→ 立即变亮
- [ ] 选「跟随系统」,把系统切到暗色 → 页面跟着变暗
- [ ] 关闭页面再打开,主题保持上次选择
- [ ] `npm run test:run` 全部通过
- [ ] `npm run type-check` 无错误

---

## 11. 风险与回滚

| 风险 | 缓解 |
|---|---|
| 内联脚本 SSR 不兼容 | 项目纯 SPA,无 SSR 风险 |
| `matchMedia` 在 jsdom 不存在 | 测试用 mock;运行时浏览器必支持 |
| Pinia 初始化时机搞错,产生两次闪烁 | 严格按 §6.2 顺序:`createPinia` → `init()` → `mount` → `watchSystem` |
| 改了 `element-overrides.scss` 导致 EP 组件对比度下降 | 暗色 token 直接复用 `css-vars.scss` 已验证过的值 |

**回滚方案**: 涉及 4 个新文件 + 4 处编辑,全部 `git revert` 一次提交即可。
