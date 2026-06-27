import request from '@/api/request'
import type { PageQuery } from '@/types/common'

export interface IotDeviceVO {
  id: number
  tenantId: number
  productId: number
  productKey: string
  productName: string
  groupId: number
  groupName?: string
  deviceKey: string
  deviceName: string
  deviceSecret: string
  protocol: string
  status: number           // 0=离线 1=在线 2=禁用
  activeTime?: string
  lastOnlineTime?: string
  lastOfflineTime?: string
  ipAddress?: string
  firmwareVersion?: string
  location?: string
  tags?: string
  description?: string
  healthScore?: number     // 0-100,蓝图 P0 健康度评分字段
  createdAt?: string
  updatedAt?: string
}

export interface IotDeviceDTO {
  id?: number
  productId: number
  deviceKey: string
  deviceName: string
  deviceSecret?: string
  groupId?: number
  location?: string
  tags?: string
  description?: string
}

export interface IotDeviceQuery extends PageQuery {
  keyword?: string
  productId?: number
  groupId?: number
  status?: number
}

export function pageDevices(params: IotDeviceQuery) {
  return request<{
    records: IotDeviceVO[]
    total: number
    size: number
    current: number
    pages: number
  }>({ url: '/iot/device/page', method: 'get', params })
}

export function getDevice(id: number, full = false) {
  return request<IotDeviceVO>({
    url: full ? `/iot/device/${id}/full` : `/iot/device/${id}`,
    method: 'get'
  })
}

export function createDevice(data: IotDeviceDTO) {
  return request<IotDeviceVO>({ url: '/iot/device', method: 'post', data })
}

export function updateDevice(data: IotDeviceDTO) {
  return request<void>({ url: '/iot/device', method: 'put', data })
}

export function deleteDevice(id: number) {
  return request<void>({ url: `/iot/device/${id}`, method: 'delete' })
}

export function resetDeviceSecret(id: number) {
  return request<string>({ url: `/iot/device/${id}/reset-secret`, method: 'post' })
}

export function toggleDeviceStatus(id: number, status: number) {
  return request<void>({
    url: `/iot/device/${id}/status/${status}`,
    method: 'put'
  })
}