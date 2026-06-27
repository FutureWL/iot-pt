/**
 * utils/ws.ts 单元测试
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'

class MockWebSocket {
  static instances: MockWebSocket[] = []
  url: string
  readyState = 0
  onopen: any = null
  onclose: any = null
  onerror: any = null
  onmessage: any = null
  constructor(url: string) {
    this.url = url
    MockWebSocket.instances.push(this)
  }
  send(data: string) { return data }
  close() { this.readyState = 3; this.onclose?.({}) }
  triggerOpen() { this.readyState = 1; this.onopen?.({}) }
  triggerMessage(data: any) { this.onmessage?.({ data: JSON.stringify(data) }) }
}
;(globalThis as any).WebSocket = MockWebSocket

import { WSClient } from '@/utils/ws'

describe('utils/ws.ts', () => {
  beforeEach(() => { MockWebSocket.instances = [] })

  it('WSClient connect 后拼接正确 URL', () => {
    const client = new WSClient('/ws/shadow')
    client.connect()
    expect(MockWebSocket.instances[0]?.url).toContain('/ws/shadow')
  })

  it('WSClient 支持 on 事件订阅与分发', () => {
    const client = new WSClient('/ws/shadow')
    const handler = vi.fn()
    client.on('shadow.update', handler)
    client.connect()
    MockWebSocket.instances[0].triggerMessage({ type: 'shadow.update', payload: { x: 1 } })
    expect(handler).toHaveBeenCalled()
  })

  it('WSClient off 取消订阅', () => {
    const client = new WSClient('/ws/shadow')
    const handler = vi.fn()
    client.on('test', handler)
    client.off('test', handler)
    expect(() => client.off('test', handler)).not.toThrow()
  })

  it('WSClient 暴露 isConnected 状态', () => {
    const client = new WSClient('/ws/shadow')
    expect(client.isConnected).toBe(false)
    client.connect()
    MockWebSocket.instances[0].triggerOpen()
    expect(client.isConnected).toBe(true)
  })

  it('WSClient close 关闭连接', () => {
    const client = new WSClient('/ws/shadow')
    client.connect()
    client.close()
    expect(MockWebSocket.instances[0].readyState).toBe(3)
  })

  it('WSClient 支持带 token 的 URL', () => {
    const client = new WSClient('/ws/shadow', 'mock-token-abc')
    client.connect()
    expect(MockWebSocket.instances[0]?.url).toContain('token=mock-token-abc')
  })
})
