import request from '@/api/request'

export type TemperatureLocation = '母排' | '触头' | '电缆接头' | '其他'

export interface TemperaturePointVO {
  sensorId: string
  deviceKey: string
  location: TemperatureLocation
  temperature: number    // ℃
  batteryLevel?: number  // %,无线传感器
  ts: string
}

export interface TemperatureStatsVO {
  max: number
  avg: number
  min: number
  alertCount: number
  sensorCount: number
}

export function getTemperatureStats(deviceId?: number) {
  return request<TemperatureStatsVO>({
    url: '/monitor/temperature/stats',
    method: 'get',
    params: { deviceId }
  })
}

export function listTemperaturePoints(params?: { deviceId?: number; location?: string }) {
  return request<TemperaturePointVO[]>({
    url: '/monitor/temperature/points',
    method: 'get',
    params
  })
}

export function getTemperatureHistory(sensorId: string, range: '1h' | '24h' | '7d' = '24h') {
  return request<{ ts: string; temperature: number }[]>({
    url: `/monitor/temperature/history/${sensorId}`,
    method: 'get',
    params: { range }
  })
}