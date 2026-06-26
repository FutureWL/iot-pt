import request from '@/api/request'

export interface SimulateRequest {
  productKey: string
  deviceKey: string
  secret: string
  type?: 'property' | 'event'
  ts?: number
  data: Record<string, any>
}

export interface SimulateResponse {
  ok: boolean
  auth?: any
  ack?: any
  stage?: 'auth' | 'io'
  error?: string
  response?: any
}

/** TCP 模拟 - 调后端,后端用 Socket 连 33410 发 */
export function simulateTcp(req: SimulateRequest) {
  return request<SimulateResponse>({
    url: '/debug/tcp/simulate',
    method: 'post',
    data: req
  })
}

/** 生成模拟数据(后端做,方便复用规则) */
export function generateMockData(payload: { seq: number; spec: Record<string, any> }) {
  return request<Record<string, any>>({
    url: '/debug/tcp/generate',
    method: 'post',
    data: payload
  })
}