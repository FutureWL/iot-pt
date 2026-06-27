/**
 * Vitest 全局 setup
 *
 * 职责:
 *   1. mock 浏览器/Node 全局副作用模块(避免污染测试)
 *   2. 注册常用 matcher / 工具扩展
 *   3. 每个测试用例前后自动重置 mock 状态
 */

import { vi, beforeEach, afterEach, expect } from 'vitest'
import { config } from '@vue/test-utils'

// ============================================================
// 1. 扩展 expect(可选:如引入 @testing-library/jest-dom)
// ============================================================

// ============================================================
// 2. 全局 mock: 副作用/网络/UI 库
// ============================================================

// js-cookie — 内存 mock,避免污染真实 cookie
vi.mock('js-cookie', () => {
  const store = new Map<string, string>()
  return {
    default: {
      get: vi.fn((key: string) => store.get(key)),
      set: vi.fn((key: string, value: string) => store.set(key, value)),
      remove: vi.fn((key: string) => store.delete(key))
    }
  }
})

// nprogress — UI 进度条
vi.mock('nprogress', () => ({
  default: {
    start: vi.fn(),
    done: vi.fn(),
    configure: vi.fn(),
    inc: vi.fn(),
    set: vi.fn()
  }
}))

// element-plus 消息提示
vi.mock('element-plus', async () => {
  const actual = await vi.importActual<typeof import('element-plus')>('element-plus')
  return {
    ...actual,
    ElMessage: {
      success: vi.fn(),
      error: vi.fn(),
      warning: vi.fn(),
      info: vi.fn()
    },
    ElMessageBox: {
      confirm: vi.fn().mockResolvedValue('confirm'),
      alert: vi.fn().mockResolvedValue('alert'),
      prompt: vi.fn().mockResolvedValue({ value: 'ok' })
    },
    ElNotification: {
      success: vi.fn(),
      error: vi.fn(),
      warning: vi.fn(),
      info: vi.fn()
    }
  }
})

// echarts — 图表库,测试中无 canvas
vi.mock('echarts', () => ({
  init: vi.fn(() => ({
    setOption: vi.fn(),
    resize: vi.fn(),
    dispose: vi.fn(),
    on: vi.fn(),
    off: vi.fn()
  })),
  registerMap: vi.fn()
}))

// ============================================================
// 3. Vue Test Utils 全局配置
// ============================================================
config.global.stubs = {
  // 全局 stub 第三方 UI 组件,简化测试
  'el-icon': true,
  'el-button': true,
  'el-input': true,
  'el-form': true,
  'el-form-item': true,
  'el-table': true,
  'el-table-column': true,
  'el-tag': true,
  'el-card': true,
  'el-dialog': true,
  'el-drawer': true,
  'el-dropdown': true,
  'el-dropdown-menu': true,
  'el-dropdown-item': true,
  'el-menu': true,
  'el-menu-item': true,
  'el-submenu': true,
  'el-breadcrumb': true,
  'el-breadcrumb-item': true,
  'el-tooltip': true,
  'e-charts': true
}

// 抑制 Vue 警告
config.global.config.warnHandler = () => {
  /* silent */
}

// ============================================================
// 4. 每个测试前后清理
// ============================================================
beforeEach(() => {
  // 清理所有 mock 调用历史
  vi.clearAllMocks()
})

afterEach(() => {
  // 恢复所有 spy 包装(避免影响下一个测试)
  vi.restoreAllMocks()
})
