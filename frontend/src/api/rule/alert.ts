import request from '@/api/request'
import type { PageQuery } from '@/types/common'
import { adaptCrudPage } from '@/api/crud'

export interface IotAlertVO {
  id: number
  tenantId: number
  ruleId: number
  ruleName?: string
  deviceId: number
  deviceKey: string
  productKey: string
  level: string               // INFO / WARN / ERROR / CRITICAL
  title: string
  content?: string
  status: number             // 0=未处理 1=已处理 2=已忽略
  handler?: string
  handleRemark?: string
  handleTime?: string
  createdAt?: string
}

export interface IotAlertQuery extends PageQuery {
  keyword?: string
  level?: string
  status?: number
  ruleId?: number
  deviceId?: number
}

export function pageAlerts(params: IotAlertQuery) {
  return request<{
    records: IotAlertVO[]
    total: number
    size: number
    current: number
    pages: number
  }>({ url: '/alert/page', method: 'get', params })
}

export function getAlert(id: string | number) {
  return request<IotAlertVO>({ url: `/alert/${id}`, method: 'get' })
}

export function handleAlert(id: number, status: 1 | 2, remark?: string) {
  return request<void>({
    url: `/alert/${id}/handle`,
    method: 'put',
    data: { status, remark }
  })
}

export function alertStats() {
  return request<Record<string, string | number>>({
    url: '/alert/stats',
    method: 'get'
  })
}

/** CrudList 适配 */
export const alertListCrud = {
  page: adaptCrudPage<IotAlertVO, IotAlertQuery>(pageAlerts)
}