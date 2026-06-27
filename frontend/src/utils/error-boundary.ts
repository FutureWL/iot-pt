/**
 * 全局错误兜底
 *
 * 业务 view 普遍使用 try/finally 而非 try/catch,当 API 失败(403 / 网络异常等)时
 * 异常会冒泡为 UnhandledRejection,即便 view 已有空状态降级。
 * 在这里静默掉这些已知可恢复的错误,避免控制台红字,同时保留 dev 模式日志便于排查。
 *
 * 调用方在 main.ts mount 之后调用 installUnhandledRejectionGuard()。
 * 返回 uninstall 函数 — 用于测试清理 + 未来 HMR 重装。
 */

interface MaybeApiError {
  isAxiosError?: boolean
  name?: string
  message?: unknown
}

/**
 * 判断 Promise rejection 是否属于"已知 API 异常"(axios / 网络层)。
 * 业务层未捕获的这类错误应被静默,而不是污染 console。
 */
export function isApiError(reason: unknown): boolean {
  if (reason == null) return false
  const r = reason as MaybeApiError
  if (r.isAxiosError === true) return true
  if (r.name === 'AxiosError') return true
  const message = typeof r.message === 'string' ? r.message : String(reason)
  return message.includes('Request failed with status code') || message.includes('网络异常')
}

/**
 * 注册 unhandledrejection 全局守卫。
 * - API 异常: dev 模式 console.warn + preventDefault;prod 完全静默
 * - 其它异常: 不拦截(让浏览器默认行为触发 console.error)
 *
 * @returns uninstall 函数
 */
export function installUnhandledRejectionGuard(): () => void {
  const handler = (event: PromiseRejectionEvent) => {
    if (!isApiError(event.reason)) return
    if (import.meta.env.DEV) {
      const reason = event.reason as MaybeApiError
      const message = typeof reason.message === 'string' ? reason.message : String(event.reason)
      console.warn('[unhandledrejection:api]', message)
    }
    event.preventDefault()
  }
  window.addEventListener('unhandledrejection', handler)
  return () => window.removeEventListener('unhandledrejection', handler)
}