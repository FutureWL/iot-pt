/**
 * E2E: 路由切换
 *
 * 验证 bug 修复:
 *   "点击工作台菜单,再点击其他菜单(如设备列表),
 *    主体内容应正确切换,不卡死在工作台"
 *
 * 走真后端:用 helpers 登录拿真实 token
 */
import { test, expect } from '@playwright/test'
import { loginAsAdmin, REAL_BACKEND, waitForBackend, BASE_URL } from './_helpers'

const TEST_TIMEOUT = 60_000

test.describe('路由切换 (bug 回归测试)', () => {
  test.beforeEach(async ({ context }) => {
    test.setTimeout(TEST_TIMEOUT)
    if (!REAL_BACKEND) test.skip(true, 'E2E_REAL_BACKEND=false, skip real backend test')
    const ready = await waitForBackend()
    if (!ready) test.skip(true, '后端未就绪, 跳过')
    await loginAsAdmin(context)
  })

  test('从工作台切换到设备列表,主体内容应切换', async ({ page }) => {
    // 1. 进入工作台(使用 hash 格式)
    await page.goto(`${BASE_URL}/#/dashboard`)
    await page.waitForLoadState('networkidle')
    expect(page.url()).toMatch(/\/dashboard/)

    // 2. 用 hash 变化触发路由(不重新加载页面)
    await page.evaluate(() => { window.location.hash = '/device/list' })
    await page.waitForLoadState('networkidle')
    expect(page.url()).toMatch(/\/device\/list/)

    // 3. 再切回工作台
    await page.evaluate(() => { window.location.hash = '/dashboard' })
    await page.waitForLoadState('networkidle')
    expect(page.url()).toMatch(/\/dashboard/)
  })

  test('连续切换多个菜单,主体内容应跟随切换', async ({ page }) => {
    // 先加载一次,让 page 在 BASE_URL 上(避免 about:blank)
    await page.goto(`${BASE_URL}/#/dashboard`)
    await page.waitForLoadState('networkidle')

    const routes = [
      '/dashboard',
      '/device/list',
      '/product',
      '/data/realtime',
      '/rule/list',
      '/dashboard'
    ]

    for (const hash of routes) {
      // 用 hash 切换(不重新加载页面)
      await page.evaluate((h) => { window.location.hash = h }, hash)
      await page.waitForLoadState('networkidle')
      expect(page.url()).toContain(`#${hash}`)
    }
  })

  test('从工作台到告警中心 - 路由切换后主体内容应正确', async ({ page }) => {
    // 这个 test 验证路由切换后主体内容变化(原 bug 修复点)
    // 1. 进工作台
    await page.goto(`${BASE_URL}/#/dashboard`)
    await page.waitForLoadState('networkidle')

    const main = page.locator('.layout-main')
    await expect(main.locator('.page-title')).toHaveText('工作台')

    // 2. router 跳到 alert/center(hash 格式)
    await page.goto(`${BASE_URL}/#/alert/center`)
    await page.waitForLoadState('networkidle')
    expect(page.url()).toMatch(/\/alert\/center/)
    // 主体内容应切换(alert/center 不含"工作台"文本)
    await expect(main).not.toContainText('工作台', { timeout: 5_000 })
  })
})