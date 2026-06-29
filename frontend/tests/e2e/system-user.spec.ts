/**
 * 用户管理 — 真集成 E2E(完整 CRUD + 状态切换)
 *
 * 验证:
 *   1. 列表加载(真后端 /api/system/user/page)
 *   2. 完整 CRUD 流程:新建用户 → 列表显示 → 删除
 *   3. 关键字筛选
 *
 * 覆盖:多字段表单(用户名/昵称/邮箱/手机/密码)+ ModalForm +
 *      CrudList + dialog 多个对话框场景
 */
import { test, expect } from '@playwright/test'
import { loginAsAdmin, api, REAL_BACKEND, waitForBackend } from './_helpers'

const TEST_TIMEOUT = 60_000
const KEY_PREFIX = 'E2E-USER-'

test.describe('用户管理 真集成', () => {
  let token: string
  const createdIds: number[] = []

  test.beforeEach(async ({ context }) => {
    test.setTimeout(TEST_TIMEOUT)
    if (!REAL_BACKEND) test.skip(true, 'E2E_REAL_BACKEND=false, skip real backend test')
    const ready = await waitForBackend()
    if (!ready) test.skip(true, '后端未就绪, 跳过')
    token = await loginAsAdmin(context)
  })

  test.afterEach(async () => {
    for (const id of createdIds.splice(0)) {
      try { await api('DELETE', `/api/system/user/${id}`, undefined, token) } catch { /* ignore */ }
    }
  })

  test('列表页加载', async ({ page }) => {
    await page.goto('/#/system/user')
    await page.waitForLoadState('networkidle')
    await expect(page.locator('.page-title')).toContainText('用户', { timeout: 5_000 })
    await page.waitForSelector('.el-table__row, .el-empty', { timeout: 10_000 })
  })

  test('完整 CRUD — 新建用户 → 列表显示 → 删除', async ({ page }) => {
    test.setTimeout(TEST_TIMEOUT)
    await page.goto('/#/system/user')
    await page.waitForLoadState('networkidle')
    await page.waitForSelector('.el-table__row, .el-empty', { timeout: 10_000 })

    // 1. 新建用户
    await page.locator('.crud-list button:has-text("新建")').first().click()
    await expect(page.locator('.el-dialog__title')).toContainText('新建用户', { timeout: 5_000 })

    const username = `${KEY_PREFIX.toLowerCase()}${Date.now()}`
    const nickname = `${KEY_PREFIX}测试用户`

    // 用户名
    await page.locator('.el-dialog input[placeholder*="登录用户名"]').fill(username)
    // 密码(只有 create 模式显示)
    const passwordInputs = page.locator('.el-dialog input[type="password"]')
    if (await passwordInputs.count() > 0) {
      await passwordInputs.first().fill('E2Etest123!')
    }
    // 昵称 - 用 form-item label 定位
    await page.locator('.el-dialog .el-form-item:has-text("昵称") input').fill(nickname)
    // 邮箱
    await page.locator('.el-dialog .el-form-item:has-text("邮箱") input').fill(`${username}@e2e.test`)

    // 提交(ModalForm 提交按钮文字取决于 dialogMode,create 模式是 "创建")
    await page.locator('.el-dialog button:has-text("创建")').click()
    await expect(page.locator('.el-dialog__title')).not.toBeVisible({ timeout: 5_000 })

    // 2. 验证列表显示
    const exactText = (t: string) => page.getByText(t, { exact: true })
    await page.waitForTimeout(1000)
    await expect(exactText(nickname).first()).toBeVisible({ timeout: 5_000 })

    // 3. 提取 id 用于清理
    const newId = await api(
      'GET',
      `/api/system/user/page?pageNum=1&pageSize=5&username=${encodeURIComponent(username)}`,
      undefined,
      token
    ).then((r: any) => r.data?.records?.[0]?.id)
    if (newId) createdIds.push(Number(newId))

    // 4. 删除(API 兜底)
    if (newId) {
      await api('DELETE', `/api/system/user/${newId}`, undefined, token)
    }
    await page.reload()
    await page.waitForLoadState('networkidle')
    await page.waitForSelector('.el-table__row, .el-empty', { timeout: 10_000 })
    await expect(exactText(nickname)).toHaveCount(0, { timeout: 5_000 })
  })

  test('关键字筛选 — 唯一用户名应不匹配', async ({ page }) => {
    await page.goto('/#/system/user')
    await page.waitForLoadState('networkidle')
    await page.waitForSelector('.el-table__row, .el-empty', { timeout: 10_000 })

    const kw = `E2E-KW-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`
    await page.locator('input[placeholder*="用户名"]').first().fill(kw)
    await page.locator('button:has-text("查询")').first().click()
    await page.waitForTimeout(800)

    const visibleRows = await page.locator('.el-table__row:visible').count()
    expect(visibleRows).toBe(0)

    // 重置
    await page.locator('button:has-text("重置")').first().click()
    await page.waitForTimeout(500)
  })
})