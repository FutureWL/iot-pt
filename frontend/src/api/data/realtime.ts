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
  /** TDengine 查到的最后上报时间戳(毫秒) */
  recentTs?: number
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

/** 轻量刷时间戳:返回 Map<deviceId, Map<identifier, lastTsMs>> */
export function getRealtimeTimestamps() {
  return request<Record<string | number, Record<string, number>>>({
    url: '/data/realtime/timestamps', method: 'get'
  })
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