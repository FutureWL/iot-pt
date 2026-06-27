/**
 * E2E: 登录状态持久化
 *
 * 验证 bug 修复:
 *   "登录成功后,刷新浏览器,应仍保持登录态,
 *    不应被踢回 /login"
 *
 * 修复点: stores/user.ts 初始化时从 cookie 恢复 token
 */
import { test, expect } from '@playwright/test'

const TOKEN_KEY = 'iot_token'
const TENANT_KEY = 'iot_tenant_id'

// 测试用 mock 凭据
const MOCK_TOKEN = 'mock-jwt-token-for-e2e'
const MOCK_TENANT_ID = '100'

test.describe('登录状态持久化 (bug 回归测试)', () => {
  test('刷新浏览器后应保持登录态,不被踢回 /login', async ({ page, context }) => {
    // 1. 模拟已登录: 写入 token 到 cookie
    await context.addCookies([
      {
        name: TOKEN_KEY,
        value: MOCK_TOKEN,
        domain: 'localhost',
        path: '/',
        expires: Math.floor(Date.now() / 1000) + 7 * 24 * 60 * 60,
        httpOnly: false,
        secure: false,
        sameSite: 'Lax'
      },
      {
        name: TENANT_KEY,
        value: MOCK_TENANT_ID,
        domain: 'localhost',
        path: '/',
        expires: Math.floor(Date.now() / 1000) + 7 * 24 * 60 * 60,
        httpOnly: false,
        secure: false,
        sameSite: 'Lax'
      }
    ])

    // 2. 访问首页,应直接进入工作台(不被踢回登录页)
    await page.goto('/')

    // 等待路由跳转稳定
    await page.waitForLoadState('networkidle')

    // 断言: URL 不应是 /login
    expect(page.url()).not.toMatch(/\/login$/)

    // 断言: cookie 中 token 仍在
    const cookies = await context.cookies()
    const tokenCookie = cookies.find((c) => c.name === TOKEN_KEY)
    expect(tokenCookie?.value).toBe(MOCK_TOKEN)

    // 3. 刷新页面
    await page.reload()
    await page.waitForLoadState('networkidle')

    // 4. 关键断言: 仍在登录态,未被踢回
    expect(page.url()).not.toMatch(/\/login$/)
    const cookiesAfter = await context.cookies()
    const tokenAfter = cookiesAfter.find((c) => c.name === TOKEN_KEY)
    expect(tokenAfter?.value).toBe(MOCK_TOKEN)
  })

  test('未登录访问受保护页面应被踢回 /login', async ({ page }) => {
    // 确保无 cookie
    await page.context().clearCookies()

    // 直接访问工作台
    await page.goto('/dashboard')

    // 应被路由守卫重定向到 /login
    await page.waitForURL(/\/login/, { timeout: 10_000 })
    expect(page.url()).toMatch(/\/login/)
  })

  test('登录页应可正常访问', async ({ page }) => {
    await page.context().clearCookies()
    await page.goto('/login')
    await expect(page).toHaveURL(/\/login/)
    // 页面应正常渲染(没白屏)
    await expect(page.locator('body')).toBeVisible()
  })
})
