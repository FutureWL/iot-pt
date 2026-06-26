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

/** 前端使用的统一分页结构 (后端 records 经此处适配为 list) */
export interface PageView<T> {
  total: number
  list: T[]
  pageNum: number
  pageSize: number
}

/** 分页响应 (MyBatis-Plus 默认序列化: records / total / size / current) */
export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}
