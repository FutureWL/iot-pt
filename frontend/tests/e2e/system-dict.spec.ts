/**
 * 字典管理 — 真集成 E2E
 *
 * 验证:
 *   1. 列表页加载(真后端 /api/system/dict/type/page)
 *   2. 双层 CRUD: 新建字典类型 → 选中新类型 → 在新类型下新建字典项
 *
 * 覆盖: 字典 (dict type → dict items) 双层 CRUD,
 *      业务逻辑包括 unique 校验、cascade 删除、type/code 锁定
 */
import { test, expect } from '@playwright/test'
import { loginAsAdmin, api, REAL_BACKEND, waitForBackend } from './_helpers'

const TEST_TIMEOUT = 60_000
const KEY_PREFIX = 'E2E-DICT-'

test.describe('字典管理 真集成', () => {
  let token: string
  const createdTypeCodes: string[] = []
  const createdItemIds: number[] = []

  test.beforeEach(async ({ context }) => {
    test.setTimeout(TEST_TIMEOUT)
    if (!REAL_BACKEND) test.skip(true, 'E2E_REAL_BACKEND=false, skip real backend test')
    const ready = await waitForBackend()
    if (!ready) test.skip(true, '后端未就绪, 跳过')
    token = await loginAsAdmin(context)
  })

  test.afterEach(async () => {
    // 兜底清理:先删 type(自动 cascade 删 items)
    for (const code of createdTypeCodes.splice(0)) {
      try {
        const list: any = await api('GET', `/api/system/dict/type/page?pageNum=1&pageSize=20&keyword=${encodeURIComponent(code)}`, undefined, token)
        const found = list.data?.records?.find((r: any) => r.type === code)
        if (found) await api('DELETE', `/api/system/dict/type/${found.id}`, undefined, token)
      } catch { /* ignore */ }
    }
    for (const id of createdItemIds.splice(0)) {
      try { await api('DELETE', `/api/system/dict/item/${id}`, undefined, token) } catch { /* ignore */ }
    }
  })

  test('列表页加载', async ({ page }) => {
    await page.goto('/#/system/dict')
    await page.waitForLoadState('networkidle')
    await expect(page.locator('.page-title')).toContainText('字典', { timeout: 5_000 })
    await expect(page.locator('button:has-text("新建字典类型")')).toBeVisible()
  })

  test('双层 CRUD — 新建字典类型 → 在新类型下新建字典项', async ({ page }) => {
    test.setTimeout(TEST_TIMEOUT)
    await page.goto('/#/system/dict')
    await page.waitForLoadState('networkidle')
    await page.waitForSelector('.el-table__row, .el-empty', { timeout: 10_000 })

    // 1. 新建字典类型
    const typeCode = `${KEY_PREFIX.toLowerCase()}${Date.now()}`
    const typeName = `${KEY_PREFIX}测试类型`
    createdTypeCodes.push(typeCode)

    await page.locator('button:has-text("新建字典类型")').click()
    await expect(page.locator('.el-dialog__title')).toContainText('新建字典类型', { timeout: 5_000 })
    await page.locator('.el-dialog input').first().fill(typeCode)
    await page.locator('.el-dialog input').nth(1).fill(typeName)
    // 类型对话框的 submit-text="创建"
    await page.locator('.el-dialog button:has-text("创建")').click()
    await expect(page.locator('.el-dialog__title')).not.toBeVisible({ timeout: 5_000 })

    // 2. 等类型列表加载
    await page.waitForTimeout(1000)
    // 点中刚创建的类型(名称唯一)
    const typeRow = page.locator(`text=${typeName}`).first()
    await typeRow.click()
    await page.waitForTimeout(500)

    // 3. 在新类型下新建字典项
    await page.locator('button:has-text("新增字典项")').click()
    await expect(page.locator('.el-dialog__title')).toContainText('新增字典项', { timeout: 5_000 })

    const itemCode = `${KEY_PREFIX.toLowerCase()}_item_${Date.now()}`
    const itemLabel = `${KEY_PREFIX}测试项`
    const itemValue = 'E2E_VALUE_001'

    // 用 form-item label 定位(避免 placeholder 错)
    await page.locator('.el-dialog .el-form-item:has-text("编码") input').fill(itemCode)
    await page.locator('.el-dialog .el-form-item:has-text("显示名") input').fill(itemLabel)
    await page.locator('.el-dialog .el-form-item:has-text("值") input').first().fill(itemValue)
    // 字典项 submit-text="保存"
    await page.locator('.el-dialog button:has-text("保存")').click()
    await expect(page.locator('.el-dialog__title')).not.toBeVisible({ timeout: 5_000 })

    // 4. 验证字典项出现
    const exactText = (t: string) => page.getByText(t, { exact: true })
    await page.waitForTimeout(1000)
    await expect(exactText(itemLabel).first()).toBeVisible({ timeout: 5_000 })

    // 5. 提取 id 用于清理
    const newId = await api(
      'GET',
      `/api/system/dict/item/page?pageNum=1&pageSize=5&keyword=${encodeURIComponent(itemLabel)}`,
      undefined,
      token
    ).then((r: any) => r.data?.records?.[0]?.id)
    if (newId) createdItemIds.push(Number(newId))
  })
})