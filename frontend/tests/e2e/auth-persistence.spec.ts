/**
 * E2E: 登录状态持久化
 *
 * 验证 bug 修复:
 *   "登录成功后,刷新浏览器,应仍保持登录态,
 *    不应被踢回 /login"
 *
 * 修复点: stores/user.ts 初始化时从 cookie 恢复 token
 *
 * 走真后端: /api/auth/login 拿真实 token,验证 cookie 跨 reload 仍在
 */
import { test, expect } from '@playwright/test'
import { loginAsAdmin, REAL_BACKEND, waitForBackend, BASE_URL, TOKEN_KEY } from './_helpers'

const TEST_TIMEOUT = 30_000

test.describe('登录状态持久化 (bug 回归测试)', () => {
  test.beforeEach(async () => {
    test.setTimeout(TEST_TIMEOUT)
    if (!REAL_BACKEND) test.skip(true, 'E2E_REAL_BACKEND=false, skip real backend test')
    const ready = await waitForBackend()
    if (!ready) test.skip(true, '后端未就绪, 跳过')
  })

  test('刷新浏览器后应保持登录态,不被踢回 /login', async ({ page, context }) => {
    // 1. 通过真后端登录,获取真实 token,写入 cookie
    await loginAsAdmin(context)

    // 2. 访问首页,应直接进入工作台(不被踢回登录页)
    await page.goto(BASE_URL)
    await page.waitForLoadState('networkidle')

    // 断言: URL 不应是 /login
    expect(page.url()).not.toMatch(/\/login$/)

    // 断言: cookie 中 token 仍在
    const cookies = await context.cookies()
    const tokenCookie = cookies.find((c) => c.name === TOKEN_KEY)
    expect(tokenCookie?.value).toBeTruthy()
    expect(tokenCookie!.value.length).toBeGreaterThan(20)  // JWT 长度通常 > 100

    // 3. 刷新页面
    await page.reload()
    await page.waitForLoadState('networkidle')

    // 4. 关键断言: 仍在登录态,未被踢回
    expect(page.url()).not.toMatch(/\/login$/)
    const cookiesAfter = await context.cookies()
    const tokenAfter = cookiesAfter.find((c) => c.name === TOKEN_KEY)
    expect(tokenAfter?.value).toBe(tokenCookie?.value)  // token 完全一致
  })

  test('未登录访问受保护页面应被踢回 /login', async ({ page }) => {
    // 确保无 cookie
    await page.context().clearCookies()

    // 直接访问工作台
    await page.goto(`${BASE_URL}/#/dashboard`)

    // 应被路由守卫重定向到 /login
    await page.waitForURL(/\/login/, { timeout: 10_000 })
    expect(page.url()).toMatch(/\/login/)
  })

  test('登录页应可正常访问', async ({ page }) => {
    await page.context().clearCookies()
    await page.goto(`${BASE_URL}/#/login`)
    await expect(page).toHaveURL(/\/login/)
    // 页面应正常渲染(没白屏)
    await expect(page.locator('body')).toBeVisible()
  })
})