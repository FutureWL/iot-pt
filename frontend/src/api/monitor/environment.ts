import request from '@/api/request'

export interface EnvironmentRealtimeVO {
  deviceId: number
  deviceKey: string
  temperature: number       // ℃
  humidity: number          // %
  waterStatus: 0 | 1        // 0=正常 1=水浸
  tiltAngle: number         // 度
  vibrationRMS: number      // g
  condensationRisk: boolean // 凝露预警
  ts: string
}

export function listEnvironmentRealtime(params?: { deviceId?: number }) {
  return request<EnvironmentRealtimeVO[]>({
    url: '/monitor/environment/realtime',
    method: 'get',
    params
  })
}

export function getEnvironmentHistory(deviceId: number, range: '1h' | '24h' | '7d' = '24h') {
  return request<EnvironmentRealtimeVO[]>({
    url: `/monitor/environment/history/${deviceId}`,
    method: 'get',
    params: { range }
  })
}