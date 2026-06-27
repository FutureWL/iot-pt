import request from '@/api/request'

export type DischargeType = '电晕' | '沿面' | '内部' | '悬浮' | '未识别'

export interface PrpdPoint {
  phase: number         // 相位 φ, 0~360
  amplitude: number     // 幅值 q, dB
  pulseCount: number    // 放电次数 n
}

export interface PrpdResultVO {
  deviceId: number
  deviceKey: string
  deviceName: string
  collectedAt: string
  pointCount: number
  dischargeType: DischargeType
  confidence: number    // 0~1
  points: PrpdPoint[]
}

export function getLatestPrpd(deviceId: number) {
  return request<PrpdResultVO>({ url: `/monitor/prpd/latest/${deviceId}`, method: 'get' })
}

export function listPrpdHistory(deviceId: number, range?: string) {
  return request<PrpdResultVO[]>({
    url: `/monitor/prpd/history/${deviceId}`,
    method: 'get',
    params: { range }
  })
}