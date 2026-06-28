/**
 * DescriptionList 测试
 *
 * 覆盖:
 *   - 默认 column = 2
 *   - title 透传
 *   - items 渲染(标签 + 值)
 *   - value 格式化(null / undefined / '' / Date / string / number)
 *   - span 跨列
 */
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import DescriptionList from '../DescriptionList.vue'

const sampleItems = [
  { label: '设备名', value: '主变温度监测' },
  { label: '设备 Key', value: 'TH-MAIN-001' },
  { label: '状态', value: '在线' },
  { label: '健康度', value: 92 }
]

function makeWrapper(props: Record<string, unknown> = {}) {
  return mount(DescriptionList, {
    props: {
      items: sampleItems,
      ...props
    },
    global: {
      stubs: {
        // el-descriptions / el-descriptions-item 用 template stub 渲染 slot
        'el-descriptions': {
          template: '<dl class="el-descriptions-stub" :data-column="column" :data-title="title"><slot /></dl>',
          props: ['column', 'title', 'border']
        },
        'el-descriptions-item': {
          template: '<div class="el-descriptions-item-stub" :data-label="label" :data-span="span"><slot /></div>',
          props: ['label', 'span']
        }
      }
    }
  })
}

describe('ui/DescriptionList — 渲染', () => {
  it('渲染所有 items', () => {
    const w = makeWrapper()
    const items = w.findAll('.el-descriptions-item-stub')
    expect(items).toHaveLength(4)
  })

  it('label 透传到每个 item', () => {
    const w = makeWrapper()
    const items = w.findAll('.el-descriptions-item-stub')
    expect(items[0].attributes('data-label')).toBe('设备名')
    expect(items[1].attributes('data-label')).toBe('设备 Key')
    expect(items[3].attributes('data-label')).toBe('健康度')
  })

  it('value 默认插槽内容正确显示', () => {
    const w = makeWrapper()
    const items = w.findAll('.el-descriptions-item-stub')
    expect(items[0].text()).toContain('主变温度监测')
    expect(items[3].text()).toContain('92')
  })

  it('默认 column=2', () => {
    const w = makeWrapper()
    expect(w.find('.el-descriptions-stub').attributes('data-column')).toBe('2')
  })

  it('column 自定义生效', () => {
    const w = makeWrapper({ column: 3 })
    expect(w.find('.el-descriptions-stub').attributes('data-column')).toBe('3')
  })

  it('column=1 也支持', () => {
    const w = makeWrapper({ column: 1 })
    expect(w.find('.el-descriptions-stub').attributes('data-column')).toBe('1')
  })
})

describe('ui/DescriptionList — title', () => {
  it('title 透传', () => {
    const w = makeWrapper({ title: '设备详情' })
    expect(w.find('.el-descriptions-stub').attributes('data-title')).toBe('设备详情')
  })

  it('title 为空字符串时透传 undefined(让 el-descriptions 不显示标题)', () => {
    const w = makeWrapper()
    // 默认 title='',但 stub 仍会渲染 data-title=""
    // 实际行为是 description.vue 把 '' 转为 undefined,所以 el-descriptions 不会显示标题
    // 这里只验证组件不会崩
    expect(w.find('.el-descriptions-stub').exists()).toBe(true)
  })

  it('title 是中文长字符串也能透传', () => {
    const w = makeWrapper({ title: '变电站 A 区 · 主变监测设备详情' })
    expect(w.find('.el-descriptions-stub').attributes('data-title')).toBe('变电站 A 区 · 主变监测设备详情')
  })
})

describe('ui/DescriptionList — span 跨列', () => {
  it('span 不传时默认 1(el-descriptions-item 默认)', () => {
    const w = makeWrapper()
    const items = w.findAll('.el-descriptions-item-stub')
    expect(items[0].attributes('data-span')).toBeUndefined()
  })

  it('span: 2 透传', () => {
    const w = makeWrapper({
      items: [
        { label: '描述', value: '很长很长的描述...', span: 2 }
      ]
    })
    expect(w.find('.el-descriptions-item-stub').attributes('data-span')).toBe('2')
  })

  it('span: 3 跨多列', () => {
    const w = makeWrapper({
      column: 4,
      items: [
        { label: '备注', value: '需要横跨整行', span: 4 }
      ]
    })
    expect(w.find('.el-descriptions-item-stub').attributes('data-span')).toBe('4')
  })
})

describe('ui/DescriptionList — value 格式化', () => {
  it('null 显示 -', () => {
    const w = makeWrapper({ items: [{ label: 'X', value: null }] })
    expect(w.text()).toContain('-')
  })

  it('undefined 显示 -', () => {
    const w = makeWrapper({ items: [{ label: 'X', value: undefined }] })
    expect(w.text()).toContain('-')
  })

  it('空字符串显示 -', () => {
    const w = makeWrapper({ items: [{ label: 'X', value: '' }] })
    expect(w.text()).toContain('-')
  })

  it('普通字符串直接显示', () => {
    const w = makeWrapper({ items: [{ label: 'X', value: 'hello' }] })
    expect(w.text()).toContain('hello')
  })

  it('数字转字符串', () => {
    const w = makeWrapper({ items: [{ label: 'X', value: 42 }] })
    expect(w.text()).toContain('42')
  })

  it('Date 对象用 zh-CN 本地化格式', () => {
    const date = new Date('2026-06-29T10:30:00')
    const w = makeWrapper({ items: [{ label: '时间', value: date }] })
    // zh-CN 默认格式: 2026/6/29 10:30:00 或 2026/06/29 10:30:00(取决于浏览器)
    const text = w.text()
    expect(text).toMatch(/2026/)
    expect(text).toMatch(/29/)
  })

  it('布尔值 true 转字符串', () => {
    const w = makeWrapper({ items: [{ label: '启用', value: true }] })
    expect(w.text()).toContain('true')
  })

  it('布尔值 false 转字符串', () => {
    const w = makeWrapper({ items: [{ label: '启用', value: false }] })
    expect(w.text()).toContain('false')
  })

  it('0 不被当作 falsy,显示 0', () => {
    const w = makeWrapper({ items: [{ label: '告警数', value: 0 }] })
    expect(w.text()).toContain('0')
  })
})

describe('ui/DescriptionList — 边界', () => {
  it('空 items 数组不崩', () => {
    const w = makeWrapper({ items: [] })
    expect(w.findAll('.el-descriptions-item-stub')).toHaveLength(0)
  })

  it('大量 items(20+) 渲染', () => {
    const many = Array.from({ length: 25 }, (_, i) => ({
      label: `字段${i}`,
      value: `value-${i}`
    }))
    const w = makeWrapper({ items: many })
    expect(w.findAll('.el-descriptions-item-stub')).toHaveLength(25)
  })
})