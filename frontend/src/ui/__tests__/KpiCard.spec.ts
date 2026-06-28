/**
 * KpiCard 组件测试
 *
 * 设计目标:统一 4 处 dashboard 卡片 (dashboard/Index, iot-console, ops/Statistics, screen)
 *
 * 接口契约:
 *   - props.title: string             // 卡片标题
 *   - props.value: number | string    // 主数值
 *   - props.suffix?: string           // 单位(%, 个, ms ...)
 *   - props.trend?: number            // 趋势百分比(正涨负跌)
 *   - props.icon?: string             // el-icon 名称
 *   - props.color?: 'primary' | 'success' | 'warning' | 'danger' | 'info'
 *   - props.loading?: boolean         // 加载态
 */
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import KpiCard from '../KpiCard.vue'

// el-statistic stub
const ElStatisticStub = defineComponent({
  name: 'ElStatistic',
  props: ['value', 'suffix'],
  setup(props, { slots }) {
    return () =>
      h('div', { 'data-testid': 'el-statistic' }, [
        h('span', { 'data-testid': 'statistic-value' }, String(props.value)),
        props.suffix ? h('span', { 'data-testid': 'statistic-suffix' }, props.suffix) : null,
        slots.prefix ? slots.prefix() : null,
        slots.suffix ? slots.suffix() : null
      ])
  }
})

interface KpiCardProps {
  title: string
  value: number | string
  suffix?: string
  trend?: number
  icon?: string
  color?: 'primary' | 'success' | 'warning' | 'danger' | 'info'
  loading?: boolean
}

function mountKpi(props: KpiCardProps) {
  return mount(KpiCard, {
    props,
    global: { stubs: { 'el-statistic': ElStatisticStub, 'el-icon': true, 'el-card': true } }
  })
}

describe('ui/KpiCard', () => {
  it('渲染 title 与 value', () => {
    const wrapper = mountKpi({ title: '在线设备', value: 128 })
    expect(wrapper.text()).toContain('在线设备')
    expect(wrapper.find('[data-testid="statistic-value"]').text()).toBe('128')
  })

  it('数字 value 接受 number 类型', () => {
    const wrapper = mountKpi({ title: '告警数', value: 42 })
    expect(wrapper.find('[data-testid="statistic-value"]').text()).toBe('42')
  })

  it('字符串 value 接受 string 类型', () => {
    const wrapper = mountKpi({ title: '运行率', value: '99.5%' })
    expect(wrapper.find('[data-testid="statistic-value"]').text()).toBe('99.5%')
  })

  it('suffix 透传到 el-statistic', () => {
    const wrapper = mountKpi({ title: '使用率', value: 80, suffix: '%' })
    expect(wrapper.find('[data-testid="statistic-suffix"]').text()).toBe('%')
  })

  it('trend > 0 显示上涨标记', () => {
    const wrapper = mountKpi({ title: '增长率', value: 100, trend: 12 })
    expect(wrapper.find('[data-testid="kpi-trend-up"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="kpi-trend-up"]').text()).toContain('12')
  })

  it('trend < 0 显示下跌标记', () => {
    const wrapper = mountKpi({ title: '下降率', value: 100, trend: -5 })
    expect(wrapper.find('[data-testid="kpi-trend-down"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="kpi-trend-down"]').text()).toContain('5')
  })

  it('trend = 0 或 undefined 不显示趋势', () => {
    const wrapper = mountKpi({ title: '平稳', value: 100 })
    expect(wrapper.find('[data-testid="kpi-trend-up"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="kpi-trend-down"]').exists()).toBe(false)
  })

  it('loading=true 时显示 loading 状态(测试组件不崩)', () => {
    const wrapper = mountKpi({ title: '加载中', value: 0, loading: true })
    expect(wrapper.exists()).toBe(true)
  })
})
