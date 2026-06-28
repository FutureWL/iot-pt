/**
 * useTable composable 测试
 *
 * 职责:封装分页列表的 loading / records / total / query / fetchPage / reset / setPage / setPageSize
 *
 * 接口契约:
 *   - 入参:CrudApi<T, Q> + initialQuery + options
 *   - 返回:{ loading; records; total; query; fetchPage; reset; setPage; setPageSize }
 */
import { describe, it, expect, vi } from 'vitest'
import { nextTick } from 'vue'
import { useTable } from '../useTable'
import type { CrudApi } from '@/api/crud'

interface Item {
  id: number
  name: string
}
interface Query {
  pageNum: number
  pageSize: number
  keyword?: string
}

function makeApi(): { api: CrudApi<Item, Query>; calls: Query[] } {
  const calls: Query[] = []
  const api: CrudApi<Item, Query> = {
    page: vi.fn(async (q: Query) => {
      calls.push({ ...q })
      return {
        records: [{ id: 1, name: 'a' }, { id: 2, name: 'b' }],
        total: 20,
        size: q.pageSize,
        current: q.pageNum,
        pages: 2
      }
    })
  }
  return { api, calls }
}

describe('composables/useTable', () => {
  it('初始化默认值 pageNum=1 / pageSize=10', () => {
    const { api } = makeApi()
    const { query } = useTable<Item, Query>(api)
    expect(query.pageNum).toBe(1)
    expect(query.pageSize).toBe(10)
  })

  it('initialQuery 合并到默认 query', () => {
    const { api } = makeApi()
    const { query } = useTable<Item, Query>(api, { keyword: 'iot' })
    expect(query.keyword).toBe('iot')
    expect(query.pageNum).toBe(1)
  })

  it('options.pageSize 覆盖默认 pageSize', () => {
    const { api } = makeApi()
    const { query } = useTable<Item, Query>(api, undefined, { pageSize: 20 })
    expect(query.pageSize).toBe(20)
  })

  it('fetchPage 调用 api.page 并写入 records/total', async () => {
    const { api } = makeApi()
    const { fetchPage, records, total, loading } = useTable<Item, Query>(api)
    await fetchPage()
    expect(records.value).toEqual([{ id: 1, name: 'a' }, { id: 2, name: 'b' }])
    expect(total.value).toBe(20)
    expect(loading.value).toBe(false)
  })

  it('fetchPage 期间 loading=true', async () => {
    let resolvePage: ((v: { records: Item[]; total: number; size: number; current: number; pages: number }) => void) | undefined
    const api: CrudApi<Item, Query> = {
      page: vi.fn(() => new Promise<{ records: Item[]; total: number; size: number; current: number; pages: number }>((resolve) => {
        resolvePage = resolve
      }))
    }
    const { fetchPage, loading } = useTable<Item, Query>(api)
    const promise = fetchPage()
    expect(loading.value).toBe(true)
    if (resolvePage) {
      resolvePage({ records: [], total: 0, size: 10, current: 1, pages: 0 })
    }
    await promise
    expect(loading.value).toBe(false)
  })

  it('fetchPage 失败时 records 清空 total=0', async () => {
    const api: CrudApi<Item, Query> = {
      page: vi.fn().mockRejectedValueOnce(new Error('Network Error'))
    }
    const { fetchPage, records, total } = useTable<Item, Query>(api)
    await fetchPage()
    expect(records.value).toEqual([])
    expect(total.value).toBe(0)
  })

  it('setPage 改 pageNum 并 fetch', async () => {
    const { api, calls } = makeApi()
    const { setPage } = useTable<Item, Query>(api)
    await setPage(3)
    expect(calls[0]?.pageNum).toBe(3)
  })

  it('setPageSize 改 pageSize 并重置 pageNum=1', async () => {
    const { api, calls } = makeApi()
    const { query, setPageSize } = useTable<Item, Query>(api)
    query.pageNum = 5
    await setPageSize(50)
    expect(query.pageSize).toBe(50)
    expect(query.pageNum).toBe(1)
    expect(calls[0]?.pageSize).toBe(50)
  })

  it('reset 还原 initialQuery 并 fetch', async () => {
    const { api, calls } = makeApi()
    const { reset, query } = useTable<Item, Query>(api, { keyword: 'iot' })
    query.pageNum = 5
    query.keyword = 'changed'
    await reset()
    expect(query.keyword).toBe('iot')
    expect(query.pageNum).toBe(1)
    expect(calls.length).toBeGreaterThan(0)
  })

  it('query 字段变化自动触发 fetchPage(深度 watch)', async () => {
    const { api, calls } = makeApi()
    const { query } = useTable<Item, Query>(api)
    query.keyword = 'new'
    await nextTick()
    await new Promise((r) => setTimeout(r, 0))
    expect(calls.length).toBeGreaterThanOrEqual(1)
    expect(calls[calls.length - 1]?.keyword).toBe('new')
  })
})
