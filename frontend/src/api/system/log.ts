import request from '@/api/request'
import type { PageQuery } from '@/types/common'
import { asCrudApi } from '@/api/crud'

export interface SysOperationLogVO {
  id: number
  userId: number
  username: string
  module: string // 模块名
  action: string // 操作类型,如 新增/修改/删除/登录
  method?: string // HTTP 方法
  url?: string // 请求路径
  ip?: string
  userAgent?: string
  params?: string // 参数 JSON
  status: number // 1=成功 0=失败
  costMs?: number
  ts: string
}

export interface OperationLogQuery extends PageQuery {
  username?: string
  module?: string
  action?: string
  status?: number
  startTime?: string
  endTime?: string
}

export function pageOperationLogs(params: OperationLogQuery) {
  return request<{
    records: SysOperationLogVO[]
    total: number
    size: number
    current: number
  }>({ url: '/system/log/page', method: 'get', params })
}

export function getOperationLog(id: number) {
  return request<SysOperationLogVO>({ url: `/system/log/${id}`, method: 'get' })
}

/** CrudList 适配 */
export const operationLogCrud = asCrudApi<SysOperationLogVO, OperationLogQuery>({
  page: async (q) => {
    const res: any = await pageOperationLogs(q)
    const data = res.data ?? {}
    return {
      records: data.records ?? [],
      total: data.total ?? 0,
      size: data.size ?? q.pageSize ?? 20,
      current: data.current ?? q.pageNum ?? 1,
      pages: data.pages ?? Math.ceil((data.total ?? 0) / (data.size ?? q.pageSize ?? 20))
    }
  }
})
