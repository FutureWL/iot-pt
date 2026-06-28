/**
 * QueryBar 组件测试
 *
 * 设计目标:统一 6+ 处 el-form 筛选条
 *
 * 接口契约:
 *   - props.filters: FilterItem[]                // 筛选字段定义
 *   - props.searchable?: boolean                 // 默认 true,显示 keyword 输入框
 *   - props.keywordPlaceholder?: string          // keyword 占位符
 *   - emit('search', query): 当前所有筛选项值
 *   - emit('reset'): 点击重置
 *
 * FilterItem:
 *   - prop: string
 *   - label: string
 *   - type?: 'input' | 'select'                  // 默认 'input'
 *   - options?: Array<{ label; value }>
 *   - placeholder?: string
 */
import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import QueryBar from '../QueryBar.vue'

const ElInputStub = defineComponent({
  name: 'ElInput',
  props: ['modelValue', 'placeholder', 'clearable'],
  emits: ['update:modelValue', 'change'],
  setup(props, { emit }) {
    return () =>
      h('input', {
        'data-testid': 'el-input',
        value: props.modelValue,
        placeholder: props.placeholder,
        onInput: (e: Event) => emit('update:modelValue', (e.target as HTMLInputElement).value)
      })
  }
})

const ElSelectStub = defineComponent({
  name: 'ElSelect',
  props: ['modelValue', 'placeholder', 'clearable'],
  emits: ['update:modelValue'],
  setup(props, { slots }) {
    return () =>
      h('div', { 'data-testid': 'el-select', 'data-value': props.modelValue }, [
        slots.default ? slots.default() : []
      ])
  }
})

const ElOptionStub = defineComponent({
  name: 'ElOption',
  props: ['label', 'value'],
  setup(props) {
    return () => h('div', { 'data-testid': 'el-option', 'data-value': props.value }, props.label)
  }
})

const ElFormStub = defineComponent({
  name: 'ElForm',
  props: ['inline', 'model'],
  setup(_props, { slots }) {
    return () => h('div', { 'data-testid': 'el-form' }, slots.default ? slots.default() : [])
  }
})

const ElFormItemStub = defineComponent({
  name: 'ElFormItem',
  props: ['label'],
  setup(props, { slots }) {
    return () =>
      h('div', { 'data-testid': 'el-form-item', 'data-label': props.label }, [
        slots.default ? slots.default() : []
      ])
  }
})

const ElButtonStub = defineComponent({
  name: 'ElButton',
  props: ['type', 'icon'],
  setup(_props, { slots, attrs }) {
    return () =>
      h(
        'button',
        { ...attrs, 'data-testid': 'el-button', type: 'button' },
        slots.default ? slots.default() : []
      )
  }
})

interface FilterItem {
  prop: string
  label: string
  type?: 'input' | 'select'
  options?: Array<{ label: string; value: string | number }>
  placeholder?: string
}

interface QueryBarProps {
  filters: FilterItem[]
  searchable?: boolean
  keywordPlaceholder?: string
}

function mountBar(props: QueryBarProps) {
  return mount(QueryBar, {
    props,
    global: {
      stubs: {
        'el-input': ElInputStub,
        'el-select': ElSelectStub,
        'el-option': ElOptionStub,
        'el-form': ElFormStub,
        'el-form-item': ElFormItemStub,
        'el-button': ElButtonStub,
        'el-icon': true,
        Search: true,
        Refresh: true
      }
    }
  })
}

describe('ui/QueryBar', () => {
  it('默认 searchable=true 时显示 keyword 输入框', () => {
    const wrapper = mountBar({ filters: [] })
    expect(wrapper.find('[data-testid="el-input"]').exists()).toBe(true)
  })

  it('searchable=false 时不显示 keyword 输入框', () => {
    const wrapper = mountBar({ filters: [], searchable: false })
    expect(wrapper.find('[data-testid="el-input"]').exists()).toBe(false)
  })

  it('按 filters 数量渲染对应 form-item(不含 keyword 和 actions)', () => {
    const filters: FilterItem[] = [
      { prop: 'status', label: '状态' },
      { prop: 'priority', label: '优先级' }
    ]
    const wrapper = mountBar({ filters, searchable: false })
    // 2 个 filter + 1 个 actions(form-item 包含查询/重置按钮)= 3
    const items = wrapper.findAll('[data-testid="el-form-item"]')
    expect(items.length).toBe(3)
  })

  it('type=input 渲染 el-input', () => {
    const filters: FilterItem[] = [{ prop: 'name', label: '名称', type: 'input' }]
    const wrapper = mountBar({ filters, searchable: false })
    expect(wrapper.find('[data-testid="el-input"]').exists()).toBe(true)
  })

  it('type=select 渲染 el-select 含 options', () => {
    const filters: FilterItem[] = [
      {
        prop: 'status',
        label: '状态',
        type: 'select',
        options: [
          { label: '待处理', value: 'PENDING' },
          { label: '已完成', value: 'COMPLETED' }
        ]
      }
    ]
    const wrapper = mountBar({ filters, searchable: false })
    expect(wrapper.find('[data-testid="el-select"]').exists()).toBe(true)
    const options = wrapper.findAll('[data-testid="el-option"]')
    expect(options.length).toBe(2)
  })

  it('点击查询按钮触发 search 事件携带 keyword', async () => {
    const onSearch = vi.fn()
    const wrapper = mount(QueryBar, {
      props: {
        filters: [{ prop: 'status', label: '状态' }],
        searchable: true,
        onSearch
      },
      global: {
        stubs: {
          'el-input': ElInputStub,
          'el-form': ElFormStub,
          'el-form-item': ElFormItemStub,
          'el-button': ElButtonStub,
          'el-icon': true,
          Search: true,
          Refresh: true
        }
      }
    })
    const buttons = wrapper.findAll('[data-testid="el-button"]')
    expect(buttons.length).toBeGreaterThan(0)
    await buttons[0]!.trigger('click')
    expect(onSearch).toHaveBeenCalledTimes(1)
    expect(onSearch.mock.calls[0]![0]).toHaveProperty('keyword', '')
  })

  it('点击重置按钮触发 reset 事件', async () => {
    const onReset = vi.fn()
    const wrapper = mount(QueryBar, {
      props: {
        filters: [],
        searchable: true,
        onReset
      },
      global: {
        stubs: {
          'el-input': ElInputStub,
          'el-form': ElFormStub,
          'el-form-item': ElFormItemStub,
          'el-button': ElButtonStub,
          'el-icon': true,
          Search: true,
          Refresh: true
        }
      }
    })
    const buttons = wrapper.findAll('[data-testid="el-button"]')
    await buttons[1]!.trigger('click')
    expect(onReset).toHaveBeenCalledTimes(1)
  })
})
