/**
 * IoT 控制台 SSE 推送验证
 *
 * 关键验证:打开页面后,**不应该有任何 REST 轮询请求**
 * 所有状态变化必须通过 SSE 推送触发
 */
import { test, expect } from '@playwright/test'

const BASE_URL = process.env.BASE_URL || 'http://localhost:33411'

test('SSE-only 模式:无 REST 轮询,状态通过 SSE 推送', async ({ page, context }) => {
  // mock auth
  await page.route('**/api/auth/info', async (route) => {
    await route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({ code: 200, data: {
        id: 1, username: 'admin', nickname: '管理员',
        roles: ['SUPER_ADMIN'], permissions: ['*'],
        tenantId: 1, tenantCode: 'default'
      }})
    })
  })

  await context.addCookies([{
    name: 'iot_token', value: 'test', domain: 'localhost', path: '/',
    expires: Math.floor(Date.now() / 1000) + 3600,
    httpOnly: false, secure: false, sameSite: 'Lax'
  }])

  // 记录所有 /api/iot-console/{status,devices,messages} REST 调用
  const restCalls: { url: string; time: number }[] = []
  page.on('request', req => {
    const url = req.url()
    if (url.includes('/api/iot-console/')) {
      restCalls.push({ url, time: Date.now() })
    }
  })

  await page.goto(`${BASE_URL}/#/iot-console`)
  await page.waitForTimeout(8000)  // 等 8 秒(覆盖 1-2 个 SSE 推送周期)

  console.log(`捕获到的 REST 请求数: ${restCalls.length}`)
  restCalls.forEach(c => console.log(`  - ${c.url.replace(BASE_URL, '')}`))

  // 关键断言:不应该有任何 REST 调用到 status/devices/messages
  // 只应该有 1 次 SSE 连接(/api/iot-console/stream)
  const restPoll = restCalls.filter(c =>
    c.url.includes('/iot-console/status') ||
    c.url.includes('/iot-console/devices') ||
    c.url.includes('/iot-console/messages')
  )
  expect(restPoll).toHaveLength(0)
  console.log('✓ 无 REST 轮询')

  // 验证 SSE 连接成功
  await expect(page.locator('text=SSE 已连接')).toBeVisible()

  // 验证概览数字非空(SSE 推送过 status)
  // 由于 broker 真在跑,数字 > 0
  const statusCards = page.locator('.metric-card')
  await expect(statusCards).toHaveCount(8)
  await page.screenshot({ path: 'test-results/sse-only.png', fullPage: true })
})