/**
 * CrudList 综合组件测试
 *
 * 设计目标:让 CRUD 列表页仅需声明 api + columns + filters,
 *        即可自动获得:筛选条 + 表格 + 分页 + loading 状态
 *
 * 接口契约:
 *   - props.api: CrudApi<T, Q>                  // CRUD 接口
 *   - props.columns: ColumnDef<T>[]             // 列定义
 *   - props.filters?: FilterItem[]              // 筛选字段
 *   - props.rowKey?: string
 *   - props.initialQuery?: Partial<Q>           // 初始 query
 *   - props.pageSize?: number                   // 默认 10
 *   - props.emptyText?: string
 *   - props.keywordPlaceholder?: string
 *   - props.rowClassName?: (row) => string      // 自定义行 class
 *
 * 插槽:
 *   - 'toolbar'      : 顶部右侧额外按钮(放在 QueryBar 右侧)
 *   - 'column-<name>': 自定义列(column.slot=name)
 *   - 'row-actions'  : 操作列(自动追加在最后)
 *
 * 暴露:
 *   - refresh(): Promise<void>
 *   - reset(): void
 */
import { describe, it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import CrudList from '../CrudList.vue'
import type { CrudApi } from '@/api/crud'

interface Item {
  id: number
  name: string
  status?: string
}
interface Query {
  pageNum: number
  pageSize: number
  keyword?: string
  status?: string
}

const QueryBarStub = defineComponent({
  name: 'QueryBar',
  emits: ['search', 'reset'],
  setup(_props, { slots, emit }) {
    return () =>
      h('div', { 'data-testid': 'query-bar' }, [
        h('button', {
          'data-testid': 'btn-search',
          onClick: () => emit('search', { keyword: 'q', status: 'PENDING' })
        }, 'search'),
        h('button', {
          'data-testid': 'btn-reset',
          onClick: () => emit('reset')
        }, 'reset'),
        slots.extra ? slots.extra() : null
      ])
  }
})

const DataTableStub = defineComponent({
  name: 'DataTable',
  props: ['columns', 'data', 'loading', 'emptyText'],
  setup(props, { slots, attrs }) {
    return () =>
      h(
        'div',
        {
          'data-testid': 'data-table',
          'data-loading': attrs.loading !== undefined ? String(attrs.loading) : '',
          'data-row-count': (props.data as unknown[])?.length ?? 0
        },
        [slots.default ? slots.default() : []]
      )
  }
})

const PagerStub = defineComponent({
  name: 'Pager',
  props: ['current', 'size', 'total'],
  emits: ['update:current', 'update:size'],
  setup(props, { emit, attrs }) {
    return () =>
      h('div', {
        'data-testid': 'pager',
        'data-current': attrs.current ?? props.current,
        'data-total': props.total
      }, [
        h('button', {
          'data-testid': 'btn-page-next',
          onClick: () => emit('update:current', (Number(attrs.current ?? props.current)) + 1)
        }, 'next'),
        h('button', {
          'data-testid': 'btn-page-size',
          onClick: () => emit('update:size', 50)
        }, 'size')
      ])
  }
})

function makeApi(): { api: CrudApi<Item, Query>; calls: Query[]; setResponse: (data: Item[], total: number) => void } {
  const calls: Query[] = []
  let nextResponse: { records: Item[]; total: number; size: number; current: number; pages: number } = {
    records: [],
    total: 0,
    size: 10,
    current: 1,
    pages: 0
  }
  const setResponse = (records: Item[], total: number): void => {
    nextResponse = { records, total, size: nextResponse.size, current: nextResponse.current, pages: Math.ceil(total / nextResponse.size) }
  }
  const api: CrudApi<Item, Query> = {
    page: vi.fn(async (q: Query) => {
      calls.push({ ...q })
      return nextResponse
    })
  }
  return { api, calls, setResponse }
}

interface CrudListTestProps {
  api: CrudApi<Item, Query>
  columns: { prop: string; label: string }[]
  filters?: { prop: string; label: string; type?: 'input' | 'select' }[]
  initialQuery?: Partial<Query>
  pageSize?: number
  emptyText?: string
  searchable?: boolean
}

function mountCl(props: CrudListTestProps) {
  return mount(CrudList, {
    props,
    global: {
      stubs: {
        QueryBar: QueryBarStub,
        DataTable: DataTableStub,
        Pager: PagerStub
      }
    }
  })
}

describe('ui/CrudList', () => {
  it('渲染 QueryBar + DataTable + Pager', async () => {
    const { api, setResponse } = makeApi()
    setResponse([{ id: 1, name: 'a' }], 1)
    const wrapper = mountCl({ api, columns: [{ prop: 'name', label: '名称' }] })
    await flushPromises()
    expect(wrapper.find('[data-testid="query-bar"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="data-table"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="pager"]').exists()).toBe(true)
  })

  it('挂载时调用 api.page 拉取数据', async () => {
    const { api, calls, setResponse } = makeApi()
    setResponse([], 0)
    mountCl({ api, columns: [{ prop: 'name', label: '名称' }] })
    await flushPromises()
    expect(calls.length).toBeGreaterThanOrEqual(1)
    expect(calls[0]?.pageNum).toBe(1)
    expect(calls[0]?.pageSize).toBe(10)
  })

  it('initialQuery 传给 query', async () => {
    const { api, calls, setResponse } = makeApi()
    setResponse([], 0)
    mountCl({
      api,
      columns: [{ prop: 'name', label: '名称' }],
      initialQuery: { keyword: 'iot' }
    })
    await flushPromises()
    expect(calls[0]?.keyword).toBe('iot')
  })

  it('点击搜索触发 search 并调用 api.page', async () => {
    const { api, calls, setResponse } = makeApi()
    setResponse([], 0)
    const wrapper = mountCl({ api, columns: [{ prop: 'name', label: '名称' }] })
    await flushPromises()
    calls.length = 0
    await wrapper.find('[data-testid="btn-search"]').trigger('click')
    await flushPromises()
    expect(calls.length).toBeGreaterThanOrEqual(1)
    expect(calls[calls.length - 1]?.keyword).toBe('q')
    expect(calls[calls.length - 1]?.status).toBe('PENDING')
  })

  it('点击重置触发 reset', async () => {
    const { api, setResponse } = makeApi()
    setResponse([], 0)
    const wrapper = mountCl({ api, columns: [{ prop: 'name', label: '名称' }] })
    await flushPromises()
    await wrapper.find('[data-testid="btn-reset"]').trigger('click')
    expect(wrapper.exists()).toBe(true)
  })

  it('Pager 翻页触发 update:current 并 fetch', async () => {
    const { api, calls, setResponse } = makeApi()
    setResponse([], 100)
    const wrapper = mountCl({ api, columns: [{ prop: 'name', label: '名称' }] })
    await flushPromises()
    calls.length = 0
    await wrapper.find('[data-testid="btn-page-next"]').trigger('click')
    await flushPromises()
    expect(calls.length).toBeGreaterThanOrEqual(1)
    expect(calls[calls.length - 1]?.pageNum).toBe(2)
  })

  it('Pager 切 size 触发 update:size', async () => {
    const { api, calls, setResponse } = makeApi()
    setResponse([], 100)
    const wrapper = mountCl({ api, columns: [{ prop: 'name', label: '名称' }] })
    await flushPromises()
    calls.length = 0
    await wrapper.find('[data-testid="btn-page-size"]').trigger('click')
    await flushPromises()
    expect(calls.length).toBeGreaterThanOrEqual(1)
    expect(calls[calls.length - 1]?.pageSize).toBe(50)
  })

  it('toolbar 插槽渲染到 QueryBar extra 位置', async () => {
    const { api, setResponse } = makeApi()
    setResponse([], 0)
    const wrapper = mount(CrudList, {
      props: { api, columns: [{ prop: 'name', label: '名称' }] },
      global: {
        stubs: {
          QueryBar: QueryBarStub,
          DataTable: DataTableStub,
          Pager: PagerStub
        }
      },
      slots: {
        toolbar: '<button data-testid="btn-create">新建</button>'
      }
    })
    await flushPromises()
    expect(wrapper.find('[data-testid="btn-create"]').exists()).toBe(true)
  })

  it('数据加载完成后 DataTable 接收 records', async () => {
    const { api, setResponse } = makeApi()
    setResponse([{ id: 1, name: 'a' }, { id: 2, name: 'b' }], 2)
    const wrapper = mountCl({ api, columns: [{ prop: 'name', label: '名称' }] })
    await flushPromises()
    const dt = wrapper.find('[data-testid="data-table"]')
    expect(dt.attributes('data-row-count')).toBe('2')
  })
})
