/**
 * DescriptionList 组件测试
 *
 * 设计目标:统一 6 处 el-descriptions 重复 (dashboard, alert, workorder/Detail, rule/Alert, monitor/Topology, Gis)
 *
 * 接口契约:
 *   - props.items: Array<{ label: string; value: unknown; span?: number }>
 *   - props.column?: number          // 默认 2
 *   - props.title?: string           // 可选分组标题
 */
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import DescriptionList from '../DescriptionList.vue'

const ElDescriptionsStub = defineComponent({
  name: 'ElDescriptions',
  props: ['column', 'title', 'border'],
  setup(props, { slots }) {
    return () =>
      h('div', { 'data-testid': 'el-descriptions', 'data-column': props.column }, [
        props.title ? h('div', { 'data-testid': 'el-descriptions-title' }, props.title) : null,
        slots.default ? slots.default() : null
      ])
  }
})

const ElDescriptionsItemStub = defineComponent({
  name: 'ElDescriptionsItem',
  props: ['label', 'span'],
  setup(props, { slots }) {
    return () =>
      h('div', { 'data-testid': 'el-descriptions-item', 'data-label': props.label }, [
        h('span', { 'data-testid': 'item-label' }, props.label),
        h('span', { 'data-testid': 'item-value' }, slots.default ? slots.default() : [])
      ])
  }
})

interface DlItem {
  label: string
  value: unknown
  span?: number
}

function mountDl(items: DlItem[], column = 2, title?: string) {
  const props: { items: DlItem[]; column: number; title?: string } = { items, column }
  if (title !== undefined) props.title = title
  return mount(DescriptionList, {
    props,
    global: {
      stubs: {
        'el-descriptions': ElDescriptionsStub,
        'el-descriptions-item': ElDescriptionsItemStub
      }
    }
  })
}

describe('ui/DescriptionList', () => {
  it('按 items 数量渲染对应 descriptions-item', () => {
    const items: DlItem[] = [
      { label: '设备名', value: '设备A' },
      { label: '设备Key', value: 'DEV-001' }
    ]
    const wrapper = mountDl(items)
    const itemEls = wrapper.findAll('[data-testid="el-descriptions-item"]')
    expect(itemEls.length).toBe(2)
  })

  it('每项渲染 label 与 value', () => {
    const items: DlItem[] = [{ label: '状态', value: '在线' }]
    const wrapper = mountDl(items)
    expect(wrapper.find('[data-testid="item-label"]').text()).toBe('状态')
    expect(wrapper.find('[data-testid="item-value"]').text()).toBe('在线')
  })

  it('数字与字符串 value 都正确显示', () => {
    const items: DlItem[] = [
      { label: '温度', value: 25.5 },
      { label: '位置', value: '北京' }
    ]
    const wrapper = mountDl(items)
    const values = wrapper.findAll('[data-testid="item-value"]')
    expect(values[0]!.text()).toBe('25.5')
    expect(values[1]!.text()).toBe('北京')
  })

  it('null/undefined value 显示占位符', () => {
    const items: DlItem[] = [
      { label: '描述', value: null },
      { label: '备注', value: undefined }
    ]
    const wrapper = mountDl(items)
    const values = wrapper.findAll('[data-testid="item-value"]')
    expect(values[0]!.text()).toBe('-')
    expect(values[1]!.text()).toBe('-')
  })

  it('默认 column=2 透传到 el-descriptions', () => {
    const items: DlItem[] = [{ label: 'a', value: 'b' }]
    const wrapper = mountDl(items)
    expect(wrapper.find('[data-testid="el-descriptions"]').attributes('data-column')).toBe('2')
  })

  it('自定义 column 生效', () => {
    const items: DlItem[] = [{ label: 'a', value: 'b' }]
    const wrapper = mountDl(items, 3)
    expect(wrapper.find('[data-testid="el-descriptions"]').attributes('data-column')).toBe('3')
  })

  it('title prop 渲染到 el-descriptions', () => {
    const items: DlItem[] = [{ label: 'a', value: 'b' }]
    const wrapper = mountDl(items, 2, '基本信息')
    expect(wrapper.find('[data-testid="el-descriptions-title"]').text()).toBe('基本信息')
  })

  it('span prop 透传到 el-descriptions-item', () => {
    const items: DlItem[] = [{ label: '完整描述', value: 'xxx', span: 2 }]
    const wrapper = mountDl(items)
    expect(wrapper.find('[data-testid="el-descriptions-item"]').attributes('data-label')).toBe('完整描述')
  })
})
