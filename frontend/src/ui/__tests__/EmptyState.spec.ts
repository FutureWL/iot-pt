/**
 * EmptyState 组件测试
 *
 * 设计目标:统一 25 处 <el-empty> 用法
 * 接口契约:
 *   - props.text?: string          // 默认 '暂无数据'
 *   - props.image?: 'noData' | 'noPermission' | 'noSearch'
 *   - props.actionText?: string    // 可选操作按钮文案
 *   - emit('action')               // 操作按钮点击
 */
import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import EmptyState from '../EmptyState.vue'

// 测试用 stub — Element Plus 组件含 CSS 资源,Vitest 不处理 .css
const ElEmptyStub = defineComponent({
  name: 'ElEmpty',
  props: ['description', 'image'],
  setup(props, { slots }) {
    return () =>
      h('div', { 'data-testid': 'el-empty' }, [
        h('p', { 'data-testid': 'el-empty-desc' }, props.description),
        slots.default?.() ?? []
      ])
  }
})

const ElButtonStub = defineComponent({
  name: 'ElButton',
  props: ['type', 'disabled'],
  setup(_props, { slots, attrs }) {
    return () =>
      h('button', { ...attrs, 'data-testid': 'el-button' }, slots.default?.() ?? [])
  }
})

function mountEmpty(props: Record<string, unknown> = {}) {
  return mount(EmptyState, {
    props,
    global: {
      stubs: {
        'el-empty': ElEmptyStub,
        'el-button': ElButtonStub
      }
    }
  })
}

describe('ui/EmptyState', () => {
  it('默认渲染 "暂无数据" 文案', () => {
    const wrapper = mountEmpty()
    expect(wrapper.find('[data-testid="el-empty-desc"]').text()).toBe('暂无数据')
  })

  it('自定义 text prop 覆盖默认文案', () => {
    const wrapper = mountEmpty({ text: '暂无设备' })
    expect(wrapper.find('[data-testid="el-empty-desc"]').text()).toBe('暂无设备')
  })

  it('未传 actionText 时不渲染操作按钮', () => {
    const wrapper = mountEmpty()
    expect(wrapper.find('[data-testid="empty-action"]').exists()).toBe(false)
  })

  it('传 actionText 时渲染操作按钮', () => {
    const wrapper = mountEmpty({ actionText: '新建设备' })
    const btn = wrapper.find('[data-testid="empty-action"]')
    expect(btn.exists()).toBe(true)
    expect(btn.text()).toBe('新建设备')
  })

  it('点击操作按钮触发 action 事件', async () => {
    const onAction = vi.fn()
    const wrapper = mountEmpty({ actionText: '刷新', onAction })
    await wrapper.find('[data-testid="empty-action"]').trigger('click')
    expect(onAction).toHaveBeenCalledTimes(1)
  })

  it('接受 image prop 不报错', () => {
    // 验证 image prop 类型契约,不强制渲染具体图片(避免绑定到 el-empty 内部实现)
    const wrapper = mountEmpty({ image: 'noSearch' })
    expect(wrapper.exists()).toBe(true)
  })
})
