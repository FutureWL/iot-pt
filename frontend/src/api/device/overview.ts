import request from '@/api/request'

export interface DeviceOverviewStats {
  total: number
  online: number
  offline: number
  fault: number
  warning: number
  healthScore: number
}

export interface ProductDistribution {
  productKey: string
  productName: string
  count: number
}

export interface DeviceOverviewVO {
  id: number
  deviceKey: string
  deviceName: string
  productName: string
  status: number
  healthScore: number
  lastOnlineTime?: string
}

/** 设备总览统计卡 + 分布数据 */
export function getOverviewStats() {
  return request<DeviceOverviewStats>({ url: '/device/overview/stats', method: 'get' })
}

export function getProductDistribution() {
  return request<ProductDistribution[]>({ url: '/device/overview/product-distribution', method: 'get' })
}

export function listOverviewDevices(params?: { keyword?: string; status?: number }) {
  return request<DeviceOverviewVO[]>({ url: '/device/overview/devices', method: 'get', params })
}