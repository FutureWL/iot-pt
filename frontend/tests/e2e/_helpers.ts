/**
 * E2E 公共 helper — 登录与 API 调用
 *
 * 设计目标:
 *   - 暴露 loginAsAdmin(): 通过真实 /api/auth/login 获取 token,
 *     写入浏览器 cookie,后续测试用此 cookie 跳过登录页
 *   - 暴露 api(): 带 token 的 fetch 封装,业务侧可写"打开页面 →
 *     调 API 创建数据 → 刷新页面验证"这种真集成测试
 *
 * 与现有 mock 模式的关系:
 *   - 现有 specs(auth-persistence / router-navigation / sse-* 等)用
 *     假 cookie + page.route mock,适合"前端行为"测试
 *   - 新的 specs(knowledge-crud / real-*)用真后端,适合"业务集成"测试
 *   - 互不冲突,各取所长
 */
import { request, type APIRequestContext, type BrowserContext, type Page } from '@playwright/test'

const TOKEN_KEY = 'iot_token'
const TENANT_CODE_DEFAULT = 'default'
const DEFAULT_ADMIN = {
  tenantCode: TENANT_CODE_DEFAULT,
  username: 'admin',
  password: '123456'
}

const BASE_URL = process.env.BASE_URL || 'http://localhost:33411'
const API_BASE = process.env.API_BASE_URL || 'http://localhost:33412'

/** 是否用真后端(默认开) */
export const REAL_BACKEND = process.env.E2E_REAL_BACKEND !== 'false'

/**
 * 通过真实后端登录,获取 token
 */
export async function fetchToken(creds: typeof DEFAULT_ADMIN = DEFAULT_ADMIN): Promise<string> {
  const res = await fetch(`${API_BASE}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(creds)
  })
  if (!res.ok) throw new Error(`login HTTP ${res.status}`)
  const json: any = await res.json()
  if (json.code !== 200) throw new Error(`login failed: ${json.message}`)
  return json.data.token
}

/**
 * 把 token 写入浏览器 cookie,后续页面请求会带上
 * (只需 context,不需要 page)
 */
export async function loginAsAdmin(context: BrowserContext): Promise<string> {
  const token = await fetchToken()
  await context.addCookies([{
    name: TOKEN_KEY,
    value: token,
    domain: 'localhost',
    path: '/',
    expires: Math.floor(Date.now() / 1000) + 7 * 24 * 60 * 60,
    httpOnly: false,
    secure: false,
    sameSite: 'Lax'
  }])
  return token
}

/**
 * 不依赖真实后端的 mock cookie(与现有 specs 兼容)
 */
export async function loginAsMock(
  context: BrowserContext,
  options: { token?: string; role?: string; username?: string } = {}
): Promise<void> {
  const { token = 'mock-jwt-token', role = 'SUPER_ADMIN', username = 'admin' } = options
  await context.addCookies([{
    name: TOKEN_KEY,
    value: token,
    domain: 'localhost',
    path: '/',
    expires: Math.floor(Date.now() / 1000) + 7 * 24 * 60 * 60,
    httpOnly: false,
    secure: false,
    sameSite: 'Lax'
  }])
  // 同步设置 localStorage(部分页面从 localStorage 取用户信息)
  // 由具体 spec 在 page 加载后调用 setUserInfoMock
  void role; void username
}

/**
 * 在已加载登录页的 page 里设置 mock 用户信息到 localStorage
 */
export async function setUserInfoMock(page: Page): Promise<void> {
  await page.evaluate(() => {
    localStorage.setItem('iot_user', JSON.stringify({
      id: 1,
      username: 'admin',
      nickname: '管理员',
      roles: ['SUPER_ADMIN'],
      permissions: ['*'],
      tenantId: 1,
      tenantCode: 'default'
    }))
  })
}

/**
 * API 调用的薄封装(带 token)
 */
export async function api(
  method: 'GET' | 'POST' | 'PUT' | 'DELETE',
  url: string,
  body?: unknown,
  token?: string
): Promise<any> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (token) headers['Authorization'] = `Bearer ${token}`
  const res = await fetch(`${API_BASE}${url}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined
  })
  const json: any = await res.json().catch(() => ({}))
  if (json.code && json.code !== 200) {
    throw new Error(`${method} ${url} failed: ${json.message || JSON.stringify(json)}`)
  }
  return json
}

/**
 * 等待后端就绪(用于测试启动时确认依赖)
 */
export async function waitForBackend(timeoutMs = 10_000): Promise<boolean> {
  const start = Date.now()
  while (Date.now() - start < timeoutMs) {
    try {
      const res = await fetch(`${API_BASE}/api/actuator/health`)
      if (res.ok) return true
    } catch { /* 重试 */ }
    await new Promise(r => setTimeout(r, 200))
  }
  return false
}

/**
 * 通用工具:从 page 上读 token
 */
export async function getTokenFromPage(page: Page): Promise<string | null> {
  return page.evaluate((key: string) => {
    const cookies = document.cookie.split(';')
    for (const c of cookies) {
      const [k, v] = c.trim().split('=')
      if (k === key) return v
    }
    return null
  }, TOKEN_KEY)
}

export { BASE_URL, API_BASE, TOKEN_KEY, DEFAULT_ADMIN }