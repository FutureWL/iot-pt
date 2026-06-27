/**
 * Playwright E2E 配置
 *
 * 启动 dev server 跑 e2e:
 *   npm run e2e         # headless
 *   npm run e2e:ui      # UI 调试
 *   npm run e2e:headed  # 有头浏览器
 *
 * 首次运行:
 *   npm run e2e:install
 */
import { defineConfig, devices } from '@playwright/test'

const PORT = Number(process.env.FRONTEND_PORT || 33411)
const BASE_URL = `http://localhost:${PORT}`

export default defineConfig({
  testDir: './tests/e2e',
  // 全局超时: 单个 test 最长允许时间
  timeout: 30_000,
  // 期望超时: expect 等待时长
  expect: { timeout: 5_000 },
  // 串行/并行
  fullyParallel: true,
  // 失败重试
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  // 报告: CI 用 github,本地用 list
  reporter: process.env.CI
    ? [['github'], ['html', { open: 'never' }]]
    : [['list'], ['html', { open: 'never' }]],
  // 公共配置
  use: {
    baseURL: BASE_URL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    // 视口
    viewport: { width: 1440, height: 900 }
  },
  // 启动 dev server
  webServer: {
    command: 'npm run dev',
    url: BASE_URL,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
    stdout: 'ignore',
    stderr: 'pipe'
  },
  // 浏览器矩阵
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] }
    },
    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'] }
    },
    {
      name: 'webkit',
      use: { ...devices['Desktop Safari'] }
    },
    // 移动端
    {
      name: 'mobile-chrome',
      use: { ...devices['Pixel 5'] }
    }
  ]
})
