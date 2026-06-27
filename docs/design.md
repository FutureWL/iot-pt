# 物联网平台 — 设计规范 (Design Guidelines)

> 适用于本项目所有前端页面与组件,新增/修改 UI 必须遵守本文档。

---

## 1. 设计原则

| 原则 | 说明 |
|---|---|
| **一致性 (Consistency)** | 颜色、间距、字号、圆角必须使用统一的 token,禁止硬编码 |
| **简洁 (Simplicity)** | 优先使用 Element Plus 默认风格,只在必要时覆盖 |
| **可读 (Readability)** | 信息密度适中,层级清晰,核心数据一眼可见 |
| **响应式 (Responsive)** | 移动端/平板/桌面三档断点统一处理 |
| **可扩展 (Extensibility)** | 设计系统支持未来扩展 (dark mode / 多品牌) |

---

## 2. 颜色系统

### 2.1 品牌色

| Token | 值 | 用途 |
|---|---|---|
| `--iot-color-primary` | `#409eff` | 主品牌色(主按钮、链接、强调) |
| `--iot-color-primary-light-3` | `#79bbff` | hover 状态 |
| `--iot-color-primary-light-9` | `#ecf5ff` | 浅背景(active/selected) |
| `--iot-color-primary-dark-2` | `#337ecc` | 按下状态 |
| `--iot-color-accent` | `#0d9488` | IoT 强调色(实时数据、在线状态) |

> 选用 Element Plus 默认蓝作为主色,**与官方组件完全兼容**,无需二次编译。

### 2.2 语义色

| Token | 值 | 用途 |
|---|---|---|
| `--iot-color-success` | `#67c23a` | 成功、在线、正常 |
| `--iot-color-warning` | `#e6a23c` | 警告、待处理 |
| `--iot-color-danger` | `#f56c6c` | 错误、离线、告警 |
| `--iot-color-info` | `#909399` | 中性提示 |

### 2.3 中性色

| Token | 值 | 用途 |
|---|---|---|
| `--iot-text-primary` | `#303133` | 主要文字(标题) |
| `--iot-text-regular` | `#606266` | 正文 |
| `--iot-text-secondary` | `#909399` | 辅助说明 |
| `--iot-text-placeholder` | `#a8abb2` | 输入框占位 |
| `--iot-text-disabled` | `#c0c4cc` | 禁用文字 |

### 2.4 边框 / 背景

| Token | 值 | 用途 |
|---|---|---|
| `--iot-border-base` | `#dcdfe6` | 默认边框 |
| `--iot-border-light` | `#e4e7ed` | 次要边框(表格行) |
| `--iot-border-lighter` | `#ebeef5` | header 下划线 |
| `--iot-bg-page` | `#f5f7fa` | 页面底色 |
| `--iot-bg-card` | `#ffffff` | 卡片/面板 |

### 2.5 侧边栏 (深色)

| Token | 值 | 用途 |
|---|---|---|
| `--iot-sidebar-bg` | `#001529` | 侧边栏背景(Ant Design Pro 风格) |
| `--iot-sidebar-bg-hover` | `#002140` | logo 区背景 |
| `--iot-sidebar-text` | `#c0c4cc` | 菜单文字 |
| `--iot-sidebar-text-active` | `#ffffff` | 选中菜单文字 |

### 2.6 ❌ 禁止

```scss
// ❌ 禁止
color: #409eff;
background: #f5f7fa;
border: 1px solid #dcdfe6;

// ✅ 正确
color: var(--iot-color-primary);
background: var(--iot-bg-page);
border: 1px solid var(--iot-border-base);
```

---

## 3. 字体系统

### 3.1 字号阶梯

| Token | 值 | 用途 |
|---|---|---|
| `--iot-font-size-xs` | 12px | 辅助说明、版权 |
| `--iot-font-size-sm` | 13px | 表格内容 |
| `--iot-font-size-base` | 14px | **正文默认** |
| `--iot-font-size-md` | 16px | 重要文字、按钮 |
| `--iot-font-size-lg` | 18px | 页面标题 |
| `--iot-font-size-xl` | 20px | 模块标题 |
| `--iot-font-size-2xl` | 24px | 大标题、弹窗标题 |
| `--iot-font-size-3xl` | 32px | 登录页主标题 |

### 3.2 字重

| Token | 值 | 用途 |
|---|---|---|
| `--el-font-weight-light` | 300 | 极少用 |
| normal | 400 | 正文 |
| medium | 500 | 表格表头、按钮 |
| semibold | 600 | 卡片标题、页面标题 |
| bold | 700 | 极少用(数据大屏数字) |

### 3.3 字体族

```
-base: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC',
       'Hiragino Sans GB', 'Microsoft YaHei', sans-serif
-code: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace
```

---

## 4. 间距系统

采用 **8 倍数栅格 + 4 微调**:

| Token | 值 | 典型场景 |
|---|---|---|
| `$spacing-0` | 0 | 无 |
| `$spacing-4` | 4px | 图标与文字间距 |
| `$spacing-8` | 8px | 紧凑列表、按钮内边距 |
| `$spacing-12` | 12px | 表单元素间距 |
| `$spacing-16` | 16px | **卡片内边距、组件间距** |
| `$spacing-24` | 24px | 模块间距 |
| `$spacing-32` | 32px | 大模块间距 |
| `$spacing-48` | 48px | 登录页内边距 |
| `$spacing-64` | 64px | 页面边距 |

### 规则

- **同一层级间距一致**(如列表项之间都用 8px)
- **不要混用 14/18/22** 这类非栅格值
- 表单垂直间距优先 `12px` 或 `16px`

---

## 5. 圆角

| Token | 值 | 用途 |
|---|---|---|
| `--iot-radius-sm` | 2px | 标签、小徽标 |
| `--iot-radius-base` | 4px | **按钮、输入框默认** |
| `--iot-radius-md` | 6px | 卡片(图标按钮) |
| `--iot-radius-lg` | 8px | 卡片、面板 |
| `--iot-radius-xl` | 12px | 大卡片 |
| `--iot-radius-2xl` | 16px | 登录卡、特殊大卡片 |
| `--iot-radius-round` | 9999px | 头像、胶囊按钮 |

---

## 6. 阴影

| Token | 值 | 用途 |
|---|---|---|
| `--iot-shadow-light` | `0 1px 2px rgba(0,0,0,.04)` | 表格行 |
| `--iot-shadow-base` | `0 2px 8px rgba(0,0,0,.06)` | 卡片默认 |
| `--iot-shadow-md` | `0 4px 12px rgba(0,0,0,.08)` | hover 卡片 |
| `--iot-shadow-lg` | `0 8px 24px rgba(0,0,0,.12)` | 下拉菜单 |
| `--iot-shadow-xl` | `0 12px 48px rgba(0,0,0,.16)` | 弹窗、Drawer |

---

## 7. 断点 (Breakpoints)

| Token | 值 | 设备 |
|---|---|---|
| `$breakpoint-xs` | 480px | 手机竖屏 |
| `$breakpoint-sm` | 768px | 手机横屏 / 平板竖屏 |
| `$breakpoint-md` | 992px | 平板横屏 / 小桌面 |
| `$breakpoint-lg` | 1200px | 桌面 |
| `$breakpoint-xl` | 1920px | 大屏 (Dashboard) |

### 使用方式

```scss
// 移动端优先
@include xs { ... }
@include sm { ... }   // ≥ 768px
@include md { ... }   // ≥ 992px
@include lg { ... }   // ≥ 1200px

// 或原生
@media (min-width: 768px) { ... }
@media (max-width: 767px) { ... }
```

---

## 8. Z-Index 层级

| Token | 值 | 用途 |
|---|---|---|
| `--iot-z-dropdown` | 1000 | 下拉菜单 |
| `--iot-z-sticky` | 1020 | 吸顶元素 |
| `--iot-z-fixed` | 1030 | 固定按钮 |
| `--iot-z-modal` | 1050 | 模态框 |
| `--iot-z-popover` | 1060 | 气泡弹层 |
| `--iot-z-tooltip` | 1070 | 提示 |

> 不要随意自定义 z-index,优先使用 token。

---

## 9. 动画

| Token | 值 | 用途 |
|---|---|---|
| `$transition-fast` | 150ms ease-in-out | 颜色切换、图标旋转 |
| `$transition-base` | 250ms ease-in-out | **默认过渡** |
| `$transition-slow` | 400ms ease-in-out | 抽屉、模态框 |

---

## 10. 组件规范

### 10.1 按钮

- **主操作**:`<el-button type="primary">` — 蓝色
- **次要操作**:`<el-button>` — 灰色
- **危险操作**:`<el-button type="danger">` — 红色,需要二次确认
- **行内图标按钮**:`<el-button link>`,或 `:icon="..." circle`
- **按钮高度**:默认 32px,表单区可用 large (40px)

### 10.2 表单

- 标签右对齐或顶对齐(根据密度)
- 必填项用 `*` 标识,EP 默认已支持
- 校验提示在下方
- **字段间距 16px 或 24px**

### 10.3 表格

- 表头背景 `#fafafa`,加粗
- 行 hover 背景 `#f5f7fa`
- 分页器右对齐
- 操作列固定在右侧,按钮用文字或图标

### 10.4 卡片

- **统一使用 `.page-card`** 容器(已在 `index.scss` 定义)
- 标题用 `.page-title`
- 工具栏用 `.page-toolbar`

### 10.5 状态色用法

| 状态 | 颜色 | 示例 |
|---|---|---|
| 在线 | `--iot-color-success` | 设备在线、连接正常 |
| 离线 | `--iot-text-disabled` | 设备离线 |
| 告警 | `--iot-color-danger` | 告警记录 |
| 警告 | `--iot-color-warning` | 待处理事项 |
| 进行中 | `--iot-color-primary` | 数据采集中 |

---

## 11. 文件组织

```
frontend/src/styles/
├── tokens.scss           # SCSS 变量(编译期)
├── css-vars.scss         # CSS 自定义属性(运行时)
├── element-overrides.scss# Element Plus 主题覆盖
└── index.scss            # 全局样式入口
```

### 11.1 使用方式

**Vue 组件 `<style>` 中:**

```vue
<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.my-component {
  color: $color-primary;
  padding: $spacing-16;
  border-radius: $radius-base;

  &:hover {
    background: var(--iot-bg-hover);  // 也可用 CSS 变量
  }
}
</style>
```

**模板中(动态样式):**

```vue
<div :style="{ color: `var(--iot-color-${status})` }">
```

---

## 12. 扩展与未来

### 12.1 Dark Mode (规划中)

CSS 变量层已为 dark mode 做好准备,只需在 `<html>` 上加 `class="dark"` 后覆盖 `:root` 即可:

```scss
// 未来 dark mode 模板
html.dark {
  --iot-bg-page: #0f172a;
  --iot-bg-card: #1e293b;
  --iot-text-primary: #e2e8f0;
  // ...
}
```

### 12.2 多品牌 (暂未规划)

通过修改 `tokens.scss` 中 `$color-primary` 即可整体换色,无需逐个改组件。

---

## 13. 改造清单(已完成)

✅ 创建 `tokens.scss` — 颜色/字号/间距/圆角/阴影/断点/动画 完整 token
✅ 创建 `css-vars.scss` — CSS 自定义属性镜像,JS 可访问
✅ 创建 `element-overrides.scss` — Element Plus 主题覆盖
✅ 重写 `index.scss` — 工具类(token-based)
✅ 重构 `MainLayout.vue` — 全部样式 token 化
✅ 重构 `login/Index.vue` — 渐变/字号/间距 token 化
✅ 重构 `User.vue` / `Role.vue` — 内联样式 token 化

**后续 TODO:**
- ⏳ 其他 view 文件(Dashboard/Realtime/History 等)的内联样式迁移
- ⏳ dark mode 完整方案
- ⏳ ECharts 主题与 token 对接

---

**修订记录:**

| 日期 | 版本 | 改动 |
|---|---|---|
| 2026-06-27 | v1.0 | 初版(由 design token 重构产生) |