/** 统一响应 */
export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
  timestamp?: number
}

/** 分页请求 */
export interface PageQuery {
  pageNum?: number
  pageSize?: number
  keyword?: string
  [k: string]: any
}

/** 分页响应 */
export interface PageResult<T> {
  total: number
  list: T[]
  pageNum: number
  pageSize: number
}
