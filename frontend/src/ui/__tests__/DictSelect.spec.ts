/**
 * DictSelect 测试 — 验证字典加载 + 选项渲染 + v-model 绑定
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import DictSelect from '../DictSelect.vue'

// ============================================================
// Mock useDict
// ============================================================

const mockLoad = vi.fn()
const mockCache = new Map<string, Record<string, string>>()

vi.mock('@/composables/useDict', () => ({
  useDict: () => ({
    load: mockLoad,
    cache: mockCache
  })
}))

// ============================================================
// el-select / el-option template stubs(覆盖全局 boolean stub)
// ============================================================

const elSelectStub = {
  template: `
    <div
      class="el-select-stub"
      :data-placeholder="placeholder"
      :data-clearable="clearable"
      :data-filterable="filterable"
      :data-disabled="disabled"
      :data-multiple="multiple"
    >
      <slot />
    </div>
  `,
  props: ['modelValue', 'placeholder', 'clearable', 'filterable', 'disabled', 'multiple'],
  emits: ['update:modelValue', 'change']
}

const elOptionStub = {
  template: `<div class="el-option-stub" :data-label="label" :data-value="value"><slot /></div>`,
  props: ['label', 'value', 'disabled']
}

// ============================================================
// Wrapper 工厂
// ============================================================

function makeWrapper(props: Record<string, unknown> = {}) {
  return mount(DictSelect, {
    props: {
      dictType: 'workorder_status',
      modelValue: undefined,
      ...props
    },
    global: {
      stubs: {
        'el-select': elSelectStub,
        'el-option': elOptionStub
      }
    }
  })
}

// ============================================================
// setup / teardown
// ============================================================

beforeEach(() => {
  mockLoad.mockReset()
  mockCache.clear()
})

afterEach(() => {
  vi.clearAllMocks()
})

// ============================================================
// 测试
// ============================================================

describe('ui/DictSelect — 字典加载', () => {
  it('mount 时自动调用 load(dictType)', async () => {
    mockLoad.mockResolvedValue({ PENDING: '待派单', DONE: '已完成' })
    makeWrapper({ dictType: 'workorder_status' })
    await flushPromises()
    expect(mockLoad).toHaveBeenCalledWith('workorder_status')
  })

  it('autoLoad=false 时不调用 load', async () => {
    makeWrapper({ dictType: 'workorder_status', autoLoad: false })
    await flushPromises()
    expect(mockLoad).not.toHaveBeenCalled()
  })

  it('缓存命中时不同步调用 load', async () => {
    // 预先放入缓存
    mockCache.set('workorder_status', { PENDING: '待派单' })
    makeWrapper({ dictType: 'workorder_status' })
    await flushPromises()
    // 缓存命中,不应再调 load
    expect(mockLoad).not.toHaveBeenCalled()
  })

  it('load 失败时不阻塞 UI(静默)', async () => {
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    mockLoad.mockRejectedValue(new Error('network error'))
    const w = makeWrapper({ dictType: 'broken_dict' })
    await flushPromises()
    // 没有选项,但不抛
    expect(w.findAll('.el-option-stub')).toHaveLength(0)
    consoleSpy.mockRestore()
  })

  it('load 完成后渲染 <el-option> 列表', async () => {
    mockLoad.mockResolvedValue({
      PENDING: '待派单',
      DONE: '已完成',
      CLOSED: '已关闭'
    })
    const w = makeWrapper({ dictType: 'workorder_status' })
    await flushPromises()
    const opts = w.findAll('.el-option-stub')
    expect(opts).toHaveLength(3)
    expect(opts[0].attributes('data-label')).toBe('待派单')
    expect(opts[0].attributes('data-value')).toBe('PENDING')
  })

  it('dictType 变化 → 重新加载', async () => {
    mockLoad.mockResolvedValue({})
    const w = makeWrapper({ dictType: 'dict_a' })
    await flushPromises()
    await w.setProps({ dictType: 'dict_b' })
    await flushPromises()
    expect(mockLoad).toHaveBeenCalledTimes(2)
    expect(mockLoad).toHaveBeenNthCalledWith(1, 'dict_a')
    expect(mockLoad).toHaveBeenNthCalledWith(2, 'dict_b')
  })
})

describe('ui/DictSelect — props 透传', () => {
  it('placeholder 透传', () => {
    const w = makeWrapper({ placeholder: '选择状态' })
    expect(w.find('.el-select-stub').attributes('data-placeholder')).toBe('选择状态')
  })

  it('clearable 默认 true', () => {
    const w = makeWrapper()
    expect(w.find('.el-select-stub').attributes('data-clearable')).toBe('true')
  })

  it('filterable 透传', () => {
    const w = makeWrapper({ filterable: true })
    expect(w.find('.el-select-stub').attributes('data-filterable')).toBe('true')
  })

  it('disabled 透传', () => {
    const w = makeWrapper({ disabled: true })
    expect(w.find('.el-select-stub').attributes('data-disabled')).toBe('true')
  })

  it('multiple 透传', () => {
    const w = makeWrapper({ multiple: true })
    expect(w.find('.el-select-stub').attributes('data-multiple')).toBe('true')
  })
})

describe('ui/DictSelect — v-model', () => {
  it('v-model 绑定到 el-select', async () => {
    mockLoad.mockResolvedValue({ PENDING: '待派单' })
    const w = makeWrapper({ modelValue: 'PENDING' })
    await flushPromises()
    expect(w.find('.el-select-stub').exists()).toBe(true)
  })

  it('el-select 触发 update:modelValue → 转发到父组件', async () => {
    mockLoad.mockResolvedValue({ PENDING: '待派单' })
    const w = makeWrapper({ modelValue: 'PENDING' })
    await flushPromises()
    const select = w.findComponent(elSelectStub)
    select.vm.$emit('update:modelValue', 'DONE')
    expect(w.emitted('update:modelValue')?.[0]).toEqual(['DONE'])
  })

  it('el-select 触发 change → emit change 事件', async () => {
    mockLoad.mockResolvedValue({})
    const w = makeWrapper()
    await flushPromises()
    const select = w.findComponent(elSelectStub)
    select.vm.$emit('change', 'CLOSED')
    expect(w.emitted('change')).toBeTruthy()
  })

  it('multiple=true 时 v-model 默认空数组', () => {
    const w = makeWrapper({ multiple: true })
    const select = w.findComponent(elSelectStub)
    expect(select.props('modelValue')).toEqual([])
  })

  it('multiple=false 时 v-model 默认 null', () => {
    const w = makeWrapper()
    const select = w.findComponent(elSelectStub)
    expect(select.props('modelValue')).toBeNull()
  })
})

describe('ui/DictSelect — optionProps 函数', () => {
  it('optionProps 接收 item,透传给 <el-option>', async () => {
    mockLoad.mockResolvedValue({ A: 'label_A', B: 'label_B' })
    const optionProps = vi.fn((item: { value: string; label: string }) => ({
      disabled: item.value === 'A'
    }))
    const w = makeWrapper({ optionProps })
    await flushPromises()
    expect(optionProps).toHaveBeenCalled()
    expect(optionProps).toHaveBeenCalledWith({ value: 'A', label: 'label_A' })
    // 第一次调用是给 A 传 disabled:true
    expect(optionProps.mock.results[0].value).toEqual({ disabled: true })
  })

  it('optionProps 不传时 el-option 不会收到额外 props', async () => {
    mockLoad.mockResolvedValue({ A: 'label_A' })
    const w = makeWrapper()
    await flushPromises()
    const opts = w.findAll('.el-option-stub')
    expect(opts[0].attributes('disabled')).toBeUndefined()
  })
})