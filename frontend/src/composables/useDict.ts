/**
 * useDict — 字典加载 composable
 *
 * 职责:
 *   1. 按 type 加载字典(GET /system/dict/{type})
 *   2. 内存缓存防止重复请求
 *   3. inflight Promise 复用防止同 type 并发请求
 *   4. 失败时清除 inflight 避免死锁
 *
 * 使用示例:
 *   const { ensureDict, dicts } = useDict()
 *   await ensureDict('workorder_status')
 *   console.log(dicts.value.PENDING) // '待处理'
 */
import { ref } from 'vue'
import request from '@/api/request'

interface DictItem {
  value: string
  label: string
}

/** 模块级缓存,跨 useDict() 调用共享 */
const cache = new Map<string, Record<string, string>>()
/** 模块级 inflight Map,防止并发同 type 重复请求 */
const inflight = new Map<string, Promise<Record<string, string>>>()

export function useDict() {
  /** 响应式字典,业务可直接绑定到模板 */
  const dicts = ref<Record<string, string>>({})

  /**
   * 加载字典(走缓存)
   * @returns value → label 的映射
   */
  async function load(type: string): Promise<Record<string, string>> {
    if (cache.has(type)) return cache.get(type)!
    if (inflight.has(type)) return inflight.get(type)!

    const promise = request<DictItem[]>({ url: `/system/dict/${type}` })
      .then((res) => {
        const map: Record<string, string> = {}
        const items = res.data ?? []
        for (const item of items) map[item.value] = item.label
        cache.set(type, map)
        inflight.delete(type)
        return map
      })
      .catch((err: unknown) => {
        inflight.delete(type)
        throw err
      })

    inflight.set(type, promise)
    return promise
  }

  /**
   * 加载并写入响应式 dicts ref
   * (单 type 场景;多 type 时直接用 load 自行管理)
   */
  async function ensureDict(type: string): Promise<void> {
    dicts.value = await load(type)
  }

  return { load, ensureDict, dicts, cache }
}
