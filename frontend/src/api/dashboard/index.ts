import request from '@/api/request'

export interface DashboardSummary {
  deviceTotal: number
  deviceByStatus: { online: number; offline: number; disabled: number }
  productTotal: number
  todayAlerts: number
  pendingAlerts: number
  shadowTotal: number
  productDistribution: { productId: number; productKey: string; productName: string; count: number }[]
  recentAlerts: { id: number; level: string; title: string; deviceKey: string; createdAt: string }[]
  recentOnlineDevices: { id: number; deviceKey: string; deviceName: string; productName: string; lastOnlineTime: string }[]
}

export function getDashboardSummary() {
  return request<DashboardSummary>({ url: '/dashboard/summary', method: 'get' })
}