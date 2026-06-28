/**
 * useTable — 分页列表 composable
 *
 * 职责:封装分页列表的 loading / records / total / query / fetchPage / reset / setPage / setPageSize
 *
 * 设计要点:
 *   - 走 CrudApi 接口契约(参见 @/api/crud)
 *   - 默认 pageNum=1 / pageSize=10
 *   - query 用 reactive + 深度 watch,字段变化自动 fetch
 *   - 失败时清空 records(避免展示陈旧数据)
 *
 * 使用示例:
 *   const { records, loading, query, setPage } = useTable(api, { keyword: '' })
 */
import { ref, reactive, watch, type Ref } from 'vue'
import type { PageQuery } from '@/types/common'
import type { CrudApi } from '@/api/crud'

interface UseTableOptions {
  /** 默认 pageSize,默认 10 */
  pageSize?: number
}

export function useTable<T, Q extends PageQuery = PageQuery>(
  api: CrudApi<T, Q>,
  initialQuery?: Partial<Q>,
  options: UseTableOptions = {}
) {
  const loading: Ref<boolean> = ref(false)
  const records: Ref<T[]> = ref([]) as Ref<T[]>
  const total: Ref<number> = ref(0)

  const query = reactive<Q>({
    pageNum: 1,
    pageSize: options.pageSize ?? 10,
    ...initialQuery
  } as Q)

  async function fetchPage(): Promise<void> {
    loading.value = true
    try {
      const res = await api.page(query as Q)
      records.value = res.records
      total.value = res.total
    } catch {
      records.value = []
      total.value = 0
    } finally {
      loading.value = false
    }
  }

  function reset(): void {
    Object.assign(query, {
      pageNum: 1,
      pageSize: options.pageSize ?? 10,
      ...initialQuery
    })
    void fetchPage()
  }

  function setPage(p: number): void {
    query.pageNum = p
    void fetchPage()
  }

  function setPageSize(s: number): void {
    query.pageSize = s
    query.pageNum = 1
    void fetchPage()
  }

  // 深度 watch:业务侧直接改 query 字段也自动 fetch
  watch(
    query,
    () => void fetchPage(),
    { deep: true }
  )

  return { loading, records, total, query, fetchPage, reset, setPage, setPageSize }
}
