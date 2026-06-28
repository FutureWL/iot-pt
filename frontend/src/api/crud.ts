/**
 * CrudApi — CRUD 列表能力接口契约
 *
 * 设计目标:让业务 API 模块实现统一接口,自动接入 <CrudList> 综合组件
 *
 * 业务模块适配示例(以 workorder 为例):
 *   import { adaptCrudPage } from '@/api/crud'
 *   export const workorderCrud = {
 *     page: adaptCrudPage(pageWorkOrders)
 *   }
 *
 * 在视图层:
 *   <CrudList :api="workorderCrud" :columns="..." :filters="..." />
 */
import type { PageQuery, PageResult } from '@/types/common'

/**
 * CRUD 列表能力
 *
 * @typeParam T 列表行 VO 类型
 * @typeParam Q 查询参数类型,默认 PageQuery(pageNum/pageSize/keyword/...)
 */
export interface CrudApi<T, Q extends PageQuery = PageQuery> {
  /** 分页查询 */
  page(query: Q): Promise<PageResult<T>>
  /** 新建/更新(可选) */
  save?: (data: Partial<T>) => Promise<unknown>
  /** 删除(可选) */
  remove?: (id: string | number) => Promise<unknown>
  /** 详情(可选) */
  detail?: (id: string | number) => Promise<T>
}

/**
 * 业务模块显式声明实现 CrudApi 契约
 * (供 type-check 校验 + IDE 自动补全)
 *
 * 注意:该函数只做类型收窄,不做请求/响应字段映射。分页场景请用 adaptCrudPage()
 */
export function asCrudApi<T, Q extends PageQuery = PageQuery>(api: {
  page(q: Q): Promise<PageResult<T>>
  save?: (d: Partial<T>) => Promise<unknown>
  remove?: (id: string | number) => Promise<unknown>
  detail?: (id: string | number) => Promise<T>
}): CrudApi<T, Q> {
  return api
}

/**
 * 原始分页接口形态(返回 ApiResponse 包装的 MyBatis-Plus Page 结果)
 */
type RawPageFn<T, Q> = (params: Q) => Promise<{
  data?: {
    records?: T[]
    total?: number | string
    size?: number | string
    current?: number | string
    pages?: number | string
  }
} | undefined | null>

/**
 * adaptCrudPage — 把"原始 page 函数"包装成 CrudApi.page
 *
 * 自动处理:
 *   1. 请求侧 pageNum/pageSize → current/size(后端 WorkOrderQuery 等用 current/size)
 *   2. 响应侧 ApiResponse.data.{records,total,size,current,pages} → PageResult
 *
 * @example
 *   export const workorderCrud = {
 *     page: adaptCrudPage<WorkOrderVO, WorkOrderQuery>(pageWorkOrders)
 *   }
 */
export function adaptCrudPage<T, Q extends PageQuery = PageQuery>(
  rawPage: RawPageFn<T, Q>
): (q: Q) => Promise<PageResult<T>> {
  return async (q: Q) => {
    // 1. 请求侧:pageNum/pageSize → current/size(后端 MyBatis-Plus 期望 current/size)
    const backendParams = { ...q } as Record<string, unknown>
    if (q.pageNum != null && backendParams.current === undefined) {
      backendParams.current = q.pageNum
    }
    if (q.pageSize != null && backendParams.size === undefined) {
      backendParams.size = q.pageSize
    }
    // Spring MVC 不会把 pageNum 绑给 current,但 axios 仍把 pageNum 一并发出也无所谓
    // — 所以这里保留两个字段,Spring 只匹配同名字段

    const res = await rawPage(backendParams as unknown as Q)
    const data = (res as any)?.data ?? {}
    const total = Number(data.total ?? 0)
    const size = Number(data.size ?? q.pageSize ?? 10)
    const current = Number(data.current ?? q.pageNum ?? 1)
    const pages = Number(
      data.pages ?? (size > 0 ? Math.ceil(total / size) : 0)
    )
    return {
      records: (data.records ?? []) as T[],
      total,
      size,
      current,
      pages
    }
  }
}

/**
 * 通用 remove 适配:CrudApi.remove 接受 string|number,业务 API 多为 number
 * 运行时会 Number(id) 真转,不只是 TS cast
 *
 * @example
 *   export const workorderCrud = {
 *     page: adaptCrudPage(...),
 *     remove: adaptCrudRemove(deleteWorkOrder),
 *   }
 */
export function adaptCrudRemove<T>(
  rawRemove: (id: number) => Promise<unknown>
): (id: string | number) => Promise<unknown> {
  return (id: string | number) => rawRemove(Number(id)) as Promise<unknown>
}

/**
 * 通用 detail 适配:CrudApi.detail 接受 string|number,业务 API 多为 number
 * 运行时会 Number(id) 真转,不只是 TS cast
 */
export function adaptCrudDetail<T>(
  rawDetail: (id: number) => Promise<T>
): (id: string | number) => Promise<T> {
  return (id: string | number) => rawDetail(Number(id))
}