/**
 * E2E: 路由切换
 *
 * 验证 bug 修复:
 *   "点击工作台菜单,再点击其他菜单(如设备列表),
 *    主体内容应正确切换,不卡死在工作台"
 */
import { test, expect } from '@playwright/test'

const TOKEN_KEY = 'iot_token'
const MOCK_TOKEN = 'mock-jwt-token-for-e2e'

async function loginAs(page: any, context: any) {
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
    }
  ])
}

/**
 * Mock 后端 API,避免路由守卫因 fetchUserInfo 失败而踢回登录页
 */
async function mockBackend(page: any) {
  // Mock 用户信息接口
  await page.route('**/api/auth/info', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        data: {
          id: 1,
          username: 'admin',
          nickname: '管理员',
          roles: ['SUPER_ADMIN'],
          permissions: ['*'],
          tenantId: 1,
          tenantCode: 'default'
        }
      })
    })
  })

  // Mock dashboard summary 接口
  await page.route('**/api/dashboard/summary', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        data: {
          deviceTotal: 100,
          deviceByStatus: { online: 80, offline: 15, disabled: 5 },
          productTotal: 10,
          tenantTotal: 1,
          userTotal: 50,
          todayAlerts: 5,
          pendingAlerts: 2
        }
      })
    })
  })
}

test.describe('路由切换 (bug 回归测试)', () => {
  test.beforeEach(async ({ page, context }) => {
    await loginAs(page, context)
    await mockBackend(page)
  })

  test('从工作台切换到设备列表,主体内容应切换', async ({ page }) => {
    // 1. 进入工作台
    await page.goto('/dashboard')
    await page.waitForLoadState('networkidle')
    expect(page.url()).toMatch(/\/dashboard/)

    // 2. 通过 URL 直接切换到设备列表(模拟菜单点击)
    await page.goto('/device/list')
    await page.waitForLoadState('networkidle')

    // 3. 关键断言: URL 已切换
    expect(page.url()).toMatch(/\/device\/list/)

    // 4. 再切回工作台
    await page.goto('/dashboard')
    await page.waitForLoadState('networkidle')
    expect(page.url()).toMatch(/\/dashboard/)
  })

  test('连续切换多个菜单,主体内容应跟随切换', async ({ page }) => {
    const routes = [
      '/dashboard',
      '/device/list',
      '/product',
      '/data/realtime',
      '/rule/list',
      '/dashboard'
    ]

    for (const route of routes) {
      await page.goto(route)
      await page.waitForLoadState('networkidle')
      expect(page.url()).toContain(route)
    }
  })

  test('点击侧边栏菜单,从工作台切到设备列表,主体内容应正确切换', async ({ page }) => {
    // 1. 模拟登录后进入工作台
    await page.goto('/dashboard')
    await page.waitForLoadState('networkidle')

    // 2. 验证当前看到的是工作台
    const main = page.locator('.layout-main')
    await expect(main.locator('.page-title')).toHaveText('工作台')

    // 3. 点击侧边栏的"设备列表"菜单项
    await page.locator('.el-menu-item:has-text("设备列表")').click()
    await page.waitForLoadState('networkidle')

    // 4. URL 应已切换
    expect(page.url()).toMatch(/\/device\/list/)

    // 5. 关键断言: 主体内容应切换为设备列表(不是工作台)
    await expect(main.locator('.page-title')).toHaveText('设备列表', { timeout: 5_000 })

    // 6. 主体内容不应再包含"工作台"标题
    await expect(main).not.toContainText('工作台')
  })
})
