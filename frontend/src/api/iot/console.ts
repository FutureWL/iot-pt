import request from '@/api/request'

// =============================================================
// IoT 控制台 API
// =============================================================
//
// REST 端点 (历史 / 手动触发):
//   POST /api/iot-console/devices/{key}/kick     踢设备
//   POST /api/iot-console/protocols/{name}/restart  重启协议
//
// SSE 流 (/api/iot-console/stream) 事件:
//   - event: "status"  data: ConsoleStatusVO     每 5 秒(在线数/TPS/总数)
//   - event: "devices" data: ConsoleDeviceVO[]  每 5 秒 + 连接时立即
//   - event: "msg"     data: ConsoleEnvelopeVO   新消息 + 连接时 50 条历史
//   - event: "ping"    data: {}                  心跳
//
// 全部状态通过 SSE 推送,前端不再轮询 REST。
// =============================================================

/** 后端 status 返回的字段 */
export interface ConsoleStatusVO {
  onlineDevices?: number
  txTotal?: number
  rxTotal?: number
  errTotal?: number
  txTps?: number
  rxTps?: number
  spyBufferSize?: number
  ts?: number
}

/** 在线设备 */
export interface ConsoleDeviceVO {
  deviceKey: string
  productKey: string
  protocol: string
  remoteAddress?: string
  connectTime?: number   // epoch ms
  lastActiveTime?: number
}

/** IoT 消息信封 */
export interface ConsoleEnvelopeVO {
  id: string
  type: string
  protocol: string
  deviceId?: number
  deviceKey: string
  productKey: string
  remoteAddress?: string
  payload?: string
  timestamp?: number
  receivedAt: number
}

/** 控制操作通用返回 */
export interface ConsoleActionResult {
  ok: boolean
  msg: string
}

export function kickDevice(deviceKey: string) {
  return request<ConsoleActionResult>({ url: `/iot-console/devices/${deviceKey}/kick`, method: 'post' })
    .then((r: any) => r.data as ConsoleActionResult)
}

export function restartProtocol(name: string) {
  return request<ConsoleActionResult>({ url: `/iot-console/protocols/${name}/restart`, method: 'post' })
    .then((r: any) => r.data as ConsoleActionResult)
}

/**
 * 创建 SSE 订阅
 *
 * 事件驱动,不再轮询 REST。所有状态通过 onStatus / onDevices 推送。
 */
export interface ConsoleStreamHandle {
  close: () => void
  source: EventSource
}

export function subscribeStream(opts: {
  baseURL?: string
  onMessage: (env: ConsoleEnvelopeVO) => void
  onStatus?: (status: ConsoleStatusVO) => void
  onDevices?: (devices: ConsoleDeviceVO[]) => void
  onError?: (e: Event) => void
  onOpen?: () => void
}): ConsoleStreamHandle {
  const base = opts.baseURL ?? (import.meta.env.VITE_API_BASE_URL || '/api')
  const url = `${base}/iot-console/stream`
  const es = new EventSource(url)

  if (opts.onOpen) es.addEventListener('open', opts.onOpen)

  es.addEventListener('status', (e: MessageEvent) => {
    try {
      opts.onStatus?.(JSON.parse(e.data) as ConsoleStatusVO)
    } catch { /* ignore */ }
  })

  es.addEventListener('devices', (e: MessageEvent) => {
    try {
      opts.onDevices?.(JSON.parse(e.data) as ConsoleDeviceVO[])
    } catch { /* ignore */ }
  })

  es.addEventListener('msg', (e: MessageEvent) => {
    try {
      opts.onMessage(JSON.parse(e.data) as ConsoleEnvelopeVO)
    } catch { /* ignore */ }
  })

  es.addEventListener('ping', () => { /* ignore */ })

  if (opts.onError) es.addEventListener('error', opts.onError)

  return {
    source: es,
    close: () => es.close()
  }
}