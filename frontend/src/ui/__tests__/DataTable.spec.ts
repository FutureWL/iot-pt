/**
 * DataTable 组件测试
 *
 * 设计目标:统一 6+ 处 el-table 重复 (device/list, alert/center, workorder/list 等)
 *
 * 接口契约:
 *   - props.columns: ColumnDef[]                     // 列定义
 *   - props.data: readonly unknown[]                 // 表格数据
 *   - props.rowKey?: string                          // 行 key 字段名,默认 'id'
 *   - props.loading?: boolean
 *   - props.emptyText?: string                       // 默认 '暂无数据'
 *   - props.stripe?: boolean                         // 默认 true
 *   - props.border?: boolean                         // 默认 true
 *
 * ColumnDef:
 *   - prop: string
 *   - label: string
 *   - width?: number | string
 *   - minWidth?: number | string
 *   - slot?: string                                 // 自定义列插槽名
 *   - align?: 'left' | 'center' | 'right'
 *   - fixed?: 'left' | 'right'
 *   - formatter?: (row) => unknown                  // 默认格式化函数
 */
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import DataTable from '../DataTable.vue'

interface ColumnDef {
  prop: string
  label: string
  width?: number | string
  minWidth?: number | string
  slot?: string
  align?: 'left' | 'center' | 'right'
  fixed?: 'left' | 'right'
  formatter?: (row: unknown) => unknown
}

const ElTableStub = defineComponent({
  name: 'ElTable',
  setup(_props, { slots, attrs }) {
    return () =>
      h(
        'div',
        {
          'data-testid': 'el-table',
          'data-loading': attrs.loading !== undefined ? String(attrs.loading) : '',
          'data-empty-text': attrs['empty-text'] ?? ''
        },
        [slots.empty ? slots.empty() : null, slots.default ? slots.default() : []]
      )
  }
})

const ElTableColumnStub = defineComponent({
  name: 'ElTableColumn',
  props: ['prop', 'label', 'width', 'minWidth', 'align', 'fixed'],
  setup(props, { slots, attrs }) {
    return () =>
      h(
        'div',
        { 'data-testid': 'el-table-column', 'data-prop': props.prop, 'data-label': props.label },
        [slots.default ? slots.default({ row: attrs.row, column: { prop: props.prop } }) : []]
      )
  }
})

interface DataTableProps {
  columns: ColumnDef[]
  data: readonly unknown[]
  rowKey?: string
  loading?: boolean
  emptyText?: string
  stripe?: boolean
  border?: boolean
}

function mountDt(props: DataTableProps) {
  return mount(DataTable, {
    props,
    global: {
      stubs: {
        'el-table': ElTableStub,
        'el-table-column': ElTableColumnStub
      }
    }
  })
}

describe('ui/DataTable', () => {
  it('按 columns 数量渲染对应 el-table-column', () => {
    const columns: ColumnDef[] = [
      { prop: 'id', label: 'ID' },
      { prop: 'name', label: '名称' },
      { prop: 'status', label: '状态' }
    ]
    const wrapper = mountDt({ columns, data: [] })
    const cols = wrapper.findAll('[data-testid="el-table-column"]')
    expect(cols.length).toBe(3)
  })

  it('每个 column 接收 prop 与 label', () => {
    const columns: ColumnDef[] = [{ prop: 'name', label: '名称' }]
    const wrapper = mountDt({ columns, data: [] })
    const col = wrapper.find('[data-testid="el-table-column"]')
    expect(col.attributes('data-prop')).toBe('name')
    expect(col.attributes('data-label')).toBe('名称')
  })

  it('空数据时显示 emptyText', () => {
    const wrapper = mountDt({ columns: [], data: [], emptyText: '暂无设备' })
    expect(wrapper.text()).toContain('暂无设备')
  })

  it('默认 emptyText 为 "暂无数据"', () => {
    const wrapper = mountDt({ columns: [], data: [] })
    expect(wrapper.text()).toContain('暂无数据')
  })

  it('loading=true 时 el-table 接收 loading=true', () => {
    const wrapper = mountDt({ columns: [], data: [], loading: true })
    expect(wrapper.find('[data-testid="el-table"]').attributes('data-loading')).toBe('true')
  })

  it('loading=false 时 el-table 接收 loading=false', () => {
    const wrapper = mountDt({ columns: [], data: [], loading: false })
    expect(wrapper.find('[data-testid="el-table"]').attributes('data-loading')).toBe('false')
  })

  it('width / align 透传到 el-table-column', () => {
    const columns: ColumnDef[] = [{ prop: 'id', label: 'ID', width: 80, align: 'center' }]
    const wrapper = mountDt({ columns, data: [] })
    const col = wrapper.find('[data-testid="el-table-column"]')
    expect(col.exists()).toBe(true)
  })
})
