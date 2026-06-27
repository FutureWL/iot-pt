import request from '@/api/request'

// =============================================================
// IoT 控制台 API
// =============================================================
//
// 后端: IotConsoleController + IotConsoleSseController
//   GET  /api/iot-console/status         概览统计
//   GET  /api/iot-console/devices        在线设备
//   GET  /api/iot-console/messages?limit=N  最近消息
//   POST /api/iot-console/devices/{key}/kick     踢设备
//   POST /api/iot-console/protocols/{name}/restart  重启协议
//   SSE  /api/iot-console/stream         实时消息流
//
// 注意:响应拦截器返回 res(code=200 时返回 ApiResponse 对象,需取 .data)
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

/** 在线设备(从 IotDeviceSession 序列化的字段) */
export interface ConsoleDeviceVO {
  deviceKey: string
  productKey: string
  protocol: string
  remoteAddress?: string
  connectTime?: number   // epoch ms
  lastActiveTime?: number
}

/** IoT 消息信封(对应 IotMessageEnvelope JSON) */
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

export function getStatus() {
  return request<ConsoleStatusVO>({ url: '/iot-console/status', method: 'get' })
    .then((r: any) => r.data as ConsoleStatusVO)
}

export function getDevices() {
  return request<ConsoleDeviceVO[]>({ url: '/iot-console/devices', method: 'get' })
    .then((r: any) => r.data as ConsoleDeviceVO[])
}

export function getMessages(limit = 100) {
  return request<ConsoleEnvelopeVO[]>({ url: `/iot-console/messages?limit=${limit}`, method: 'get' })
    .then((r: any) => r.data as ConsoleEnvelopeVO[])
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
 * 返回 EventSource 实例 + close 函数。
 * 收到的事件:
 *   - "msg"  data=ConsoleEnvelopeVO
 *   - "ping" data={}  心跳
 */
export interface ConsoleStreamHandle {
  close: () => void
  source: EventSource
}

export function subscribeStream(opts: {
  baseURL?: string
  onMessage: (env: ConsoleEnvelopeVO) => void
  onError?: (e: Event) => void
  onOpen?: () => void
}): ConsoleStreamHandle {
  // EventSource 不支持自定义 header → SecurityConfig 已把 /iot-console/** 放行
  const base = opts.baseURL ?? (import.meta.env.VITE_API_BASE_URL || '/api')
  const url = `${base}/iot-console/stream`
  const es = new EventSource(url)

  if (opts.onOpen) es.addEventListener('open', opts.onOpen)
  es.addEventListener('msg', (e: MessageEvent) => {
    try {
      const data = JSON.parse(e.data) as ConsoleEnvelopeVO
      opts.onMessage(data)
    } catch {
      // ignore parse error
    }
  })
  es.addEventListener('ping', () => { /* ignore */ })
  // SSE error - EventSource 会自动重连,这里只在主动 close 后触发
  if (opts.onError) es.addEventListener('error', opts.onError)

  return {
    source: es,
    close: () => es.close()
  }
}