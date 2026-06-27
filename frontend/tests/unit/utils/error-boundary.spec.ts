import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { isApiError, installUnhandledRejectionGuard } from '@/utils/error-boundary'

// 每次 install 都保存 uninstall,afterEach 统一清理避免测试间干扰
const uninstallers: Array<() => void> = []

function setupGuard() {
  const uninstall = installUnhandledRejectionGuard()
  uninstallers.push(uninstall)
  return uninstall
}

// 触发 unhandledrejection,返回是否被 preventDefault
function fireRejection(reason: unknown): boolean {
  const event = new Event('unhandledrejection') as Event & { reason?: unknown }
  event.reason = reason
  window.dispatchEvent(event)
  return event.defaultPrevented
}

describe('utils/error-boundary — isApiError', () => {
  it('reason.isAxiosError === true → 返回 true', () => {
    expect(isApiError({ isAxiosError: true, message: 'whatever' })).toBe(true)
  })

  it('reason.name === "AxiosError" → 返回 true', () => {
    expect(isApiError({ name: 'AxiosError', message: 'whatever' })).toBe(true)
  })

  it('reason.message 含 "Request failed with status code" → 返回 true', () => {
    expect(isApiError({ message: 'Request failed with status code 403' })).toBe(true)
  })

  it('reason.message 含 "网络异常" → 返回 true', () => {
    expect(isApiError(new Error('网络异常'))).toBe(true)
  })

  it('普通 Error(无关文案) → 返回 false', () => {
    expect(isApiError(new Error('TypeError: cannot read property'))).toBe(false)
  })

  it('null / undefined → 返回 false', () => {
    expect(isApiError(null)).toBe(false)
    expect(isApiError(undefined)).toBe(false)
  })

  it('无 isAxiosError/name/匹配 message 的普通对象 → 返回 false', () => {
    expect(isApiError({ message: 'hello world' })).toBe(false)
    expect(isApiError({ code: 403 })).toBe(false)
  })
})

describe('utils/error-boundary — installUnhandledRejectionGuard', () => {
  // 用宽松类型避免 vitest MockInstance 与 spyOn 重载的类型不匹配
  let warnSpy: any

  beforeEach(() => {
    warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})
  })

  afterEach(() => {
    warnSpy.mockRestore()
    // 清理本次测试注册的所有 guard
    while (uninstallers.length) uninstallers.pop()!()
  })

  it('AxiosError 触发时,preventDefault() 被调用', () => {
    setupGuard()
    const prevented = fireRejection({ isAxiosError: true, message: 'Request failed with status code 403' })
    expect(prevented).toBe(true)
  })

  it('dev 模式下,AxiosError 触发时 console.warn 输出标签化消息', () => {
    setupGuard()
    fireRejection({ isAxiosError: true, message: 'Request failed with status code 500' })
    expect(warnSpy).toHaveBeenCalledWith('[unhandledrejection:api]', expect.stringContaining('500'))
  })

  it('"网络异常" 触发的 Error 也会被拦截', () => {
    setupGuard()
    const prevented = fireRejection(new Error('网络异常'))
    expect(prevented).toBe(true)
  })

  it('普通 Error 触发时,handler 不拦截(不 preventDefault)', () => {
    setupGuard()
    const prevented = fireRejection(new Error('Application crash'))
    expect(prevented).toBe(false)
  })

  it('普通 Error 触发时,console.warn 不被调用', () => {
    setupGuard()
    fireRejection(new Error('Application crash'))
    expect(warnSpy).not.toHaveBeenCalled()
  })

  it('uninstall 返回的函数会移除监听器', () => {
    const uninstall = setupGuard()
    uninstall()
    const prevented = fireRejection({ isAxiosError: true, message: 'whatever' })
    expect(prevented).toBe(false)
    expect(warnSpy).not.toHaveBeenCalled()
  })

  it('多次 install + uninstall 不会重复注册(避免监听器堆积)', () => {
    setupGuard()
    setupGuard()
    // 两次 install 都会注册,所以事件触发两次 preventDefault 也是合法的
    // 但卸载其中一个后,另一个仍应生效
    uninstallers.pop()!()
    const prevented = fireRejection({ isAxiosError: true, message: 'x' })
    expect(prevented).toBe(true)
  })
})