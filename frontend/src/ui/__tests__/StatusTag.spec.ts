/**
 * StatusTag 组件测试
 *
 * 设计目标:统一 11+ 处 statusMap 内联 (device/List, alert/Center 等)
 *
 * 接口契约:
 *   - props.value: string | number           // 状态值
 *   - props.label?: string                   // 显式 label 覆盖,缺省显示 value
 *   - props.typeMap?: Record<string, StatusType>  // 自定义映射
 *   - props.size?: 'small' | 'default' | 'large'
 *   - 内置默认映射覆盖常见状态 (PENDING/COMPLETED/...)
 */
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import StatusTag from '../StatusTag.vue'

// 测试用 stub — Element Plus 含 CSS,Vitest 不处理
const ElTagStub = defineComponent({
  name: 'ElTag',
  props: ['type', 'size', 'effect'],
  setup(props, { slots }) {
    return () =>
      h(
        'span',
        { 'data-testid': 'el-tag', 'data-type': props.type },
        slots.default?.() ?? []
      )
  }
})

interface StatusTagProps {
  value: string | number
  label?: string
  typeMap?: Record<string, 'success' | 'warning' | 'danger' | 'info' | 'primary'>
  size?: 'small' | 'default' | 'large'
}

function mountTag(props: StatusTagProps) {
  return mount(StatusTag, {
    props,
    global: { stubs: { 'el-tag': ElTagStub } }
  })
}

describe('ui/StatusTag', () => {
  it('显示 value 字符串', () => {
    const wrapper = mountTag({ value: 'PENDING' })
    expect(wrapper.find('[data-testid="el-tag"]').text()).toBe('PENDING')
  })

  it('显示 value 数字', () => {
    const wrapper = mountTag({ value: 1 })
    expect(wrapper.find('[data-testid="el-tag"]').text()).toBe('1')
  })

  it('显式 label 覆盖默认显示', () => {
    const wrapper = mountTag({ value: 'PENDING', label: '待处理' })
    expect(wrapper.find('[data-testid="el-tag"]').text()).toBe('待处理')
  })

  it('PENDING 映射到 warning 类型', () => {
    const wrapper = mountTag({ value: 'PENDING' })
    expect(wrapper.find('[data-testid="el-tag"]').attributes('data-type')).toBe('warning')
  })

  it('COMPLETED 映射到 success 类型', () => {
    const wrapper = mountTag({ value: 'COMPLETED' })
    expect(wrapper.find('[data-testid="el-tag"]').attributes('data-type')).toBe('success')
  })

  it('OFFLINE/0 映射到 info 类型', () => {
    const wrapper = mountTag({ value: 0 })
    expect(wrapper.find('[data-testid="el-tag"]').attributes('data-type')).toBe('info')
  })

  it('未知值降级到 info 类型', () => {
    const wrapper = mountTag({ value: 'GIBBERISH' })
    expect(wrapper.find('[data-testid="el-tag"]').attributes('data-type')).toBe('info')
  })

  it('typeMap 覆盖内置映射', () => {
    const wrapper = mountTag({
      value: 'CUSTOM',
      typeMap: { CUSTOM: 'danger' as const }
    })
    expect(wrapper.find('[data-testid="el-tag"]').attributes('data-type')).toBe('danger')
  })

  it('size prop 透传到 el-tag', () => {
    const wrapper = mountTag({ value: 'PENDING', size: 'small' })
    expect(wrapper.find('[data-testid="el-tag"]').attributes('data-type')).toBe('warning')
  })
})
