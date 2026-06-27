import request from '@/api/request'

/** 局放监测 — 实时数据 + 历史趋势 */
export interface PdRealtimeVO {
  deviceId: number
  deviceKey: string
  deviceName: string
  amplitude: number       // pC
  pulseCount: number
  channelType: 'UHF' | 'HFCT'
  phaseAngle: number      // 度,0~360
  threshold: number       // 告警阈值
  status: 'normal' | 'warning' | 'alarm'
  ts: string
}

export interface PdHistoryPoint {
  ts: string
  amplitude: number
  pulseCount: number
}

export function getPdRealtime(deviceId?: number) {
  return request<PdRealtimeVO[]>({
    url: '/monitor/pd/realtime',
    method: 'get',
    params: { deviceId }
  })
}

export function getPdHistory(deviceId: number, range: '1h' | '24h' | '7d' = '24h') {
  return request<PdHistoryPoint[]>({
    url: `/monitor/pd/history/${deviceId}`,
    method: 'get',
    params: { range }
  })
}