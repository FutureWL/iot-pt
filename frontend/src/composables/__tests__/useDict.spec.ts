/**
 * useDict composable 测试
 *
 * 职责:按 type 加载字典 + 内存缓存 + 复用 inflight Promise 防止并发重复请求
 *
 * 接口契约:
 *   - load(type): Promise<Record<string, string>>   // 加载字典(走缓存)
 *   - ensureDict(type): Promise<void>                // 加载并写入 ref
 *   - cache: Map                                     // 暴露缓存供调试
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'

// vi.mock 会被 hoisted 到顶部,mockRequest 必须用 vi.hoisted 提前声明
const { mockRequest } = vi.hoisted(() => ({ mockRequest: vi.fn() }))

vi.mock('@/api/request', () => ({
  default: mockRequest
}))

import { useDict } from '../useDict'

describe('composables/useDict', () => {
  beforeEach(() => {
    mockRequest.mockReset()
    // 重置模块级缓存(测试间隔离)
    const { cache } = useDict()
    cache.clear()
  })

  it('load 调用 request 拉取字典并返回映射', async () => {
    mockRequest.mockResolvedValueOnce({
      data: [
        { value: '0', label: '离线' },
        { value: '1', label: '在线' }
      ]
    })
    const { load } = useDict()
    const result = await load('device_status')
    expect(result).toEqual({ '0': '离线', '1': '在线' })
    expect(mockRequest).toHaveBeenCalledTimes(1)
    expect(mockRequest).toHaveBeenCalledWith({ url: '/system/dict/device_status' })
  })

  it('load 命中缓存不再发请求', async () => {
    mockRequest.mockResolvedValueOnce({
      data: [{ value: '0', label: '离线' }]
    })
    const { load } = useDict()
    await load('device_status')
    await load('device_status')
    expect(mockRequest).toHaveBeenCalledTimes(1)
  })

  it('load 并发同 type 复用 inflight Promise(只发一次请求)', async () => {
    let resolveFn: (v: unknown) => void = () => {}
    mockRequest.mockReturnValueOnce(
      new Promise((resolve) => {
        resolveFn = resolve
      })
    )
    const { load } = useDict()
    const p1 = load('alert_level')
    const p2 = load('alert_level')
    resolveFn({ data: [{ value: 'CRITICAL', label: '严重' }] })
    const [r1, r2] = await Promise.all([p1, p2])
    expect(r1).toEqual(r2)
    expect(mockRequest).toHaveBeenCalledTimes(1)
  })

  it('load 失败时清除 inflight 并抛错', async () => {
    mockRequest.mockRejectedValueOnce(new Error('Network Error'))
    const { load } = useDict()
    await expect(load('bad_dict')).rejects.toThrow('Network Error')
    // 失败后再次 load 应能重新请求(不进死锁)
    mockRequest.mockResolvedValueOnce({ data: [{ value: 'a', label: 'A' }] })
    const result = await load('bad_dict')
    expect(result).toEqual({ a: 'A' })
    expect(mockRequest).toHaveBeenCalledTimes(2)
  })

  it('ensureDict 写入响应式 dicts ref', async () => {
    mockRequest.mockResolvedValueOnce({
      data: [{ value: 'PENDING', label: '待处理' }]
    })
    const { ensureDict, dicts } = useDict()
    expect(dicts.value).toEqual({})
    await ensureDict('workorder_status')
    expect(dicts.value).toEqual({ PENDING: '待处理' })
  })

  it('空 data 不抛错,返回空对象', async () => {
    mockRequest.mockResolvedValueOnce({ data: [] })
    const { load } = useDict()
    const result = await load('empty_dict')
    expect(result).toEqual({})
  })

  it('null data 不抛错,返回空对象', async () => {
    mockRequest.mockResolvedValueOnce({ data: null })
    const { load } = useDict()
    const result = await load('null_dict')
    expect(result).toEqual({})
  })
})
