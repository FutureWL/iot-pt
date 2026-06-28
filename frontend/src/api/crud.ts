/**
 * CrudApi — CRUD 列表能力接口契约
 *
 * 设计目标:让业务 API 模块实现统一接口,自动接入 <CrudList> 综合组件
 *
 * 业务模块适配示例(以 workorder 为例):
 *   import { asCrudApi } from '@/api/crud'
 *   export const workorderCrud = asCrudApi<WorkOrderVO, WorkOrderQuery>({
 *     page: pageWorkOrders,
 *     save: createWorkOrder,
 *     remove: ...
 *   })
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
 */
export function asCrudApi<T, Q extends PageQuery = PageQuery>(api: {
  page(q: Q): Promise<PageResult<T>>
  save?: (d: Partial<T>) => Promise<unknown>
  remove?: (id: string | number) => Promise<unknown>
  detail?: (id: string | number) => Promise<T>
}): CrudApi<T, Q> {
  return api
}
