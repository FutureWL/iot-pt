/**
 * WebSocket 客户端(自动重连 + 事件订阅)
 *
 * 路径默认从环境变量 VITE_WS_BASE_URL 读取(后端 context-path 是 /api,
 *   所以默认 '/api/ws'),与后端 Spring WebSocket endpoint 对齐。
 *
 * 用法:
 *   const ws = new WSClient()                    // 用默认路径
 *   const ws = new WSClient('/api/ws/other')     // 自定义路径
 *   ws.on('shadow.update', (evt) => { ... })
 *   ws.connect()
 *   ...
 *   ws.close()
 */
import { ElMessage } from 'element-plus'

type Handler = (data: any) => void

/** 默认 WS 前缀:对齐后端 server.servlet.context-path=/api */
const DEFAULT_WS_BASE = import.meta.env.VITE_WS_BASE_URL || '/api/ws'

export class WSClient {
  private url: string
  private token: string
  private ws: WebSocket | null = null
  private handlers = new Map<string, Set<Handler>>()
  private reconnectTimer: any = null
  private pingTimer: any = null
  private reconnectDelay = 1000
  private maxReconnectDelay = 30000
  private connected = ref(false)
  /** 最近一次失败原因(用于排查) */
  lastError = ref<string>('')

  constructor(path = `${DEFAULT_WS_BASE}/shadow`, token?: string) {
    this.token = token || this.readToken()
    this.url = this.buildUrl(path)
  }

  get isConnected() {
    return this.connected.value
  }

  private buildUrl(path: string): string {
    const proto = location.protocol === 'https:' ? 'wss' : 'ws'
    const base = `${proto}://${location.host}${path}`
    return this.token ? `${base}?token=${encodeURIComponent(this.token)}` : base
  }

  private readToken(): string {
    // 从 cookie 取
    const m = document.cookie.match(/(?:^|;\s*)iot_token=([^;]+)/)
    if (m) return decodeURIComponent(m[1])
    // 备用: localStorage
    return localStorage.getItem('iot_token') || ''
  }

  on(type: string, handler: Handler) {
    if (!this.handlers.has(type)) this.handlers.set(type, new Set())
    this.handlers.get(type)!.add(handler)
  }

  off(type: string, handler?: Handler) {
    if (!this.handlers.has(type)) return
    if (handler) this.handlers.get(type)!.delete(handler)
    else this.handlers.delete(type)
  }

  private emit(type: string, data: any) {
    this.handlers.get(type)?.forEach((h) => {
      try {
        h(data)
      } catch (e) {
        console.error('WS handler err', e)
      }
    })
    this.handlers.get('*')?.forEach((h) => {
      try {
        h({ type, ...data })
      } catch (e) {
        console.error('WS handler err', e)
      }
    })
  }

  connect() {
    if (
      this.ws &&
      (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING)
    ) {
      return
    }
    console.debug('[WS] 连接中:', this.url.replace(/token=.*/, 'token=***'))
    try {
      this.ws = new WebSocket(this.url)
    } catch (e: any) {
      console.error('[WS] 创建失败', e?.message || e)
      this.lastError.value = `创建失败: ${e?.message || e}`
      this.scheduleReconnect()
      return
    }
    this.ws.onopen = () => {
      console.debug('[WS] 已连接', this.url.replace(/token=.*/, 'token=***'))
      this.connected.value = true
      this.lastError.value = ''
      this.reconnectDelay = 1000
      this.emit('connected', {})
      this.startPing()
    }
    this.ws.onmessage = (ev) => {
      try {
        const data = JSON.parse(ev.data)
        this.emit(data.type || 'message', data)
      } catch (e) {
        console.warn('[WS] 解析失败', e)
      }
    }
    this.ws.onerror = (e) => {
      const msg = (e as any)?.message || '连接错误'
      console.warn('[WS] 错误', msg, this.url.replace(/token=.*/, 'token=***'))
      this.lastError.value = msg
    }
    this.ws.onclose = (ev) => {
      // 记录 code + reason,排查路径/鉴权问题时很关键
      console.debug('[WS] 断开', `code=${ev.code} reason=${ev.reason || '(空)'}`)
      this.connected.value = false
      this.lastError.value = `code=${ev.code}${ev.reason ? ' ' + ev.reason : ''}`
      this.stopPing()
      this.emit('disconnected', { code: ev.code, reason: ev.reason })
      this.scheduleReconnect()
    }
  }

  private scheduleReconnect() {
    if (this.reconnectTimer) return
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null
      this.reconnectDelay = Math.min(this.reconnectDelay * 2, this.maxReconnectDelay)
      this.connect()
    }, this.reconnectDelay)
  }

  private startPing() {
    this.stopPing()
    this.pingTimer = setInterval(() => {
      if (this.ws?.readyState === WebSocket.OPEN) {
        try {
          this.ws.send('ping')
        } catch {}
      }
    }, 25000)
  }

  private stopPing() {
    if (this.pingTimer) {
      clearInterval(this.pingTimer)
      this.pingTimer = null
    }
  }

  close() {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    this.stopPing()
    if (this.ws) {
      this.ws.onclose = null // 阻止重连
      try {
        this.ws.close()
      } catch {}
      this.ws = null
    }
    this.connected.value = false
  }
}

// Vue ref 兼容(避免 import ref 重复)
import { ref } from 'vue'
