import request from '@/api/request'
import type { PageQuery } from '@/types/common'
import { adaptCrudPage } from '@/api/crud'

export type WorkOrderStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'OVERDUE' | 'CLOSED'
export type WorkOrderPriority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT'

export interface WorkOrderVO {
  id: number
  workOrderNo: string
  alertId?: number
  deviceId: number
  deviceKey: string
  deviceName: string
  title: string
  description?: string
  priority: WorkOrderPriority
  status: WorkOrderStatus
  assignee?: string
  creator: string
  slaDeadline?: string
  createdAt: string
  completedAt?: string
}

export interface WorkOrderLogVO {
  id: number
  workOrderId: number
  operator: string
  action: string
  remark?: string
  ts: string
}

export interface WorkOrderQuery extends PageQuery {
  status?: WorkOrderStatus
  priority?: WorkOrderPriority
  assignee?: string
  deviceId?: number
}

export interface WorkOrderStatsVO {
  pending: number
  processing: number
  completed: number
  overdue: number
}

export function pageWorkOrders(params: WorkOrderQuery) {
  return request<{
    records: WorkOrderVO[]
    total: number
    size: number
    current: number
  }>({ url: '/workorder/page', method: 'get', params })
}

export function getWorkOrder(id: string | number) {
  return request<WorkOrderVO>({ url: `/workorder/${id}`, method: 'get' })
}

export function getWorkOrderLogs(workOrderId: number) {
  return request<WorkOrderLogVO[]>({ url: `/workorder/${workOrderId}/logs`, method: 'get' })
}

export function getWorkOrderStats() {
  return request<WorkOrderStatsVO>({ url: '/workorder/stats', method: 'get' })
}

export function createWorkOrder(data: Partial<WorkOrderVO>) {
  return request<{ id: number }>({ url: '/workorder', method: 'post', data })
}

export function updateWorkOrder(data: Partial<WorkOrderVO>) {
  return request<void>({ url: '/workorder', method: 'put', data })
}

export function assignWorkOrder(id: number, assignee: string) {
  return request<void>({ url: `/workorder/${id}/assign`, method: 'put', data: { assignee } })
}

export function completeWorkOrder(id: number, remark?: string) {
  return request<void>({ url: `/workorder/${id}/complete`, method: 'put', data: { remark } })
}

/** CrudList 适配:WorkOrder 后端暂未提供 DELETE,故不含 remove */
export const workorderCrud = {
  page: adaptCrudPage<WorkOrderVO, WorkOrderQuery>(pageWorkOrders)
}