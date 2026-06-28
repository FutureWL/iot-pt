import { describe, it, expect, vi } from 'vitest'
import { adaptCrudPage, adaptCrudRemove, adaptCrudDetail, asCrudApi } from '@/api/crud'
import type { PageQuery } from '@/types/common'

interface Row { id: number; title: string }
interface Q extends PageQuery {
  status?: string
}

describe('api/crud — adaptCrudPage', () => {
  it('请求侧:把 pageNum/pageSize 映射为 current/size 传给后端', async () => {
    const captured: any = {}
    const raw = vi.fn(async (params: any) => {
      Object.assign(captured, params)
      return { data: { records: [], total: 0, size: 10, current: 1 } }
    })
    const page = adaptCrudPage<Row, Q>(raw)
    await page({ pageNum: 2, pageSize: 50, status: 'PENDING' } as Q)

    expect(captured.current).toBe(2)
    expect(captured.size).toBe(50)
    expect(captured.status).toBe('PENDING')
    // 保留 pageNum/pageSize 也无所谓(后端不识别则忽略)
    expect(captured.pageNum).toBe(2)
    expect(captured.pageSize).toBe(50)
  })

  it('响应侧:从 ApiResponse.data 中解出 PageResult 字段', async () => {
    const raw = vi.fn(async () => ({
      data: {
        records: [{ id: 1, title: 'a' }],
        total: 100,
        size: 10,
        current: 2,
        pages: 10
      }
    }))
    const page = adaptCrudPage<Row, Q>(raw)
    const res = await page({ pageNum: 2, pageSize: 10 } as Q)

    expect(res.records).toHaveLength(1)
    expect(res.total).toBe(100)
    expect(res.size).toBe(10)
    expect(res.current).toBe(2)
    expect(res.pages).toBe(10)
  })

  it('响应字段缺失时回落到 q.pageNum/q.pageSize,再回落到 1/10', async () => {
    const raw = vi.fn(async () => ({ data: { records: [], total: 0 } }))
    const page = adaptCrudPage<Row, Q>(raw)

    // 仅有 pageNum/pageSize
    let res = await page({ pageNum: 3, pageSize: 25 } as Q)
    expect(res.current).toBe(3)
    expect(res.size).toBe(25)
    expect(res.pages).toBe(0)  // 0 total → 0 pages

    // 全空
    res = await page({} as Q)
    expect(res.current).toBe(1)
    expect(res.size).toBe(10)
  })

  it('响应字段缺失 + 0 total 时 pages 用 ceil(0/x)=0,不会 NaN', async () => {
    const raw = vi.fn(async () => ({ data: { records: [], total: 0, size: 10, current: 1 } }))
    const page = adaptCrudPage<Row, Q>(raw)
    const res = await page({} as Q)
    expect(res.pages).toBe(0)
    expect(Number.isFinite(res.pages)).toBe(true)
  })

  it('res 为 null/undefined 时不崩', async () => {
    const raw = vi.fn(async () => null)
    const page = adaptCrudPage<Row, Q>(raw)
    const res = await page({} as Q)
    expect(res.records).toEqual([])
    expect(res.total).toBe(0)
  })

  it('响应 total 是字符串时正确转 number', async () => {
    const raw = vi.fn(async () => ({
      data: { records: [], total: '42', size: '20', current: '1', pages: '3' }
    }))
    const page = adaptCrudPage<Row, Q>(raw)
    const res = await page({} as Q)
    expect(res.total).toBe(42)
    expect(res.size).toBe(20)
    expect(res.current).toBe(1)
    expect(res.pages).toBe(3)
  })
})

describe('api/crud — adaptCrudRemove / adaptCrudDetail', () => {
  it('adaptCrudRemove 把 string|number 强转为 number 调原函数', async () => {
    const raw = vi.fn(async (id: number) => id)
    const remove = adaptCrudRemove<Row>(raw)
    expect(await remove(123)).toBe(123)
    expect(raw).toHaveBeenCalledWith(123)
  })

  it('adaptCrudDetail 同上', async () => {
    const raw = vi.fn(async (id: number) => ({ id, title: 't' }))
    const detail = adaptCrudDetail<Row>(raw)
    const r = await detail('7')
    // raw fn 返回 id=7(number),detail 包装不修改返回
    expect(r).toEqual({ id: 7, title: 't' })
    expect(raw).toHaveBeenCalledWith(7)  // string '7' 已转 number
  })
})

describe('api/crud — asCrudApi 类型收窄', () => {
  it('原样返回 api(仅类型收窄)', () => {
    const api = { page: async () => ({ records: [], total: 0, size: 10, current: 1, pages: 0 }) }
    expect(asCrudApi(api)).toBe(api)
  })
})