import request from '@/api/request'

/** 实时数据:每个设备的当前属性快照 */
export interface RealtimeProperty {
  identifier: string
  name?: string
  type?: string
  unit?: string
  accessMode?: string
  value: string | null
  updatedAt?: string
}

export interface RealtimeDevice {
  deviceId: number
  deviceKey: string
  deviceName: string
  productKey: string
  productName: string
  status: number
  lastOnlineTime?: string
  location?: string
  tags?: string
  properties: RealtimeProperty[]
}

export function getRealtime() {
  return request<RealtimeDevice[]>({ url: '/data/realtime', method: 'get' })
}

export function getHistory(deviceId: number, identifier: string, type: string,
                          startMs: number, endMs: number) {
  return request<{ ts: string; value: string }[]>({
    url: '/data/history',
    method: 'get',
    params: { deviceId, identifier, type, startMs, endMs }
  })
}

export function getHistoryStats(deviceId: number, identifier: string, type: string,
                                startMs: number, endMs: number) {
  return request<Record<string, string>>({
    url: '/data/history/stats',
    method: 'get',
    params: { deviceId, identifier, type, startMs, endMs }
  })
}