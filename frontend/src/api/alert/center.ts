import request from '@/api/request'
import type { PageQuery } from '@/types/common'
import { adaptCrudPage } from '@/api/crud'

/** 告警级别(电力专项:注意/异常/严重/紧急) */
export type AlertLevel = 'NOTICE' | 'ABNORMAL' | 'SERIOUS' | 'URGENT'

export interface AlertCenterVO {
  id: number
  level: AlertLevel
  alertType: string
  deviceId: number
  deviceKey: string
  deviceName: string
  productKey: string
  title: string
  content?: string
  handler?: string
  status: number             // 0=未处理 1=已处理 2=已忽略
  workOrderId?: number
  alertTime: string
  handleTime?: string
}

export interface AlertCenterQuery extends PageQuery {
  level?: AlertLevel
  status?: number
  deviceId?: number
}

export interface AlertLevelStat {
  level: AlertLevel
  count: number
}

export function pageAlertsCenter(params: AlertCenterQuery) {
  return request<{
    records: AlertCenterVO[]
    total: number
    size: number
    current: number
  }>({ url: '/alert/center/page', method: 'get', params })
}

export function getAlertLevelStats() {
  return request<AlertLevelStat[]>({ url: '/alert/center/stats', method: 'get' })
}

export function handleAlertCenter(id: number, status: 1 | 2, remark?: string) {
  return request<void>({
    url: `/alert/center/${id}/handle`,
    method: 'put',
    data: { status, remark }
  })
}

export function createWorkOrderFromAlert(alertId: number) {
  return request<{ workOrderId: number }>({
    url: `/alert/center/${alertId}/create-work-order`,
    method: 'post'
  })
}

/** CrudList 适配 */
export const alertCrud = {
  page: adaptCrudPage<AlertCenterVO, AlertCenterQuery>(pageAlertsCenter)
}