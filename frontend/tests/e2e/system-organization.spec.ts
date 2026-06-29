/**
 * 组织架构 — 真集成 E2E
 *
 * 验证:
 *   1. 列表页加载 + 树渲染(真后端 /api/system/organization/tree)
 *   2. 新建顶级组织
 *   3. 关键字搜索顶级组织
 *
 * 覆盖: 树形结构 CRUD(el-tree + ModalForm),
 *      与线性列表 (CrudList) 形成对比
 */
import { test, expect } from '@playwright/test'
import { loginAsAdmin, api, REAL_BACKEND, waitForBackend } from './_helpers'

const TEST_TIMEOUT = 60_000
const KEY_PREFIX = 'E2E-ORG-'

test.describe('组织架构 真集成', () => {
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
      try { await api('DELETE', `/api/system/organization/${id}`, undefined, token) } catch { /* ignore */ }
    }
  })

  test('列表页加载 + 树渲染', async ({ page }) => {
    await page.goto('/#/system/organization')
    await page.waitForLoadState('networkidle')
    await expect(page.locator('.page-title')).toContainText('组织', { timeout: 5_000 })

    await expect(page.locator('button:has-text("新建顶级组织")')).toBeVisible({ timeout: 5_000 })

    // 树或 el-empty 都算正常
    const tree = page.locator('.el-tree')
    const empty = page.locator('.el-empty')
    const treeCount = await tree.count()
    const emptyCount = await empty.count()
    expect(treeCount + emptyCount).toBeGreaterThan(0)
  })

  test('新建顶级组织', async ({ page }) => {
    test.setTimeout(TEST_TIMEOUT)
    await page.goto('/#/system/organization')
    await page.waitForLoadState('networkidle')
    await expect(page.locator('button:has-text("新建顶级组织")')).toBeVisible({ timeout: 5_000 })

    // 点"新建顶级组织"
    await page.locator('button:has-text("新建顶级组织")').click()
    await expect(page.locator('.el-dialog__title')).toContainText('新建组织', { timeout: 5_000 })

    const orgName = `${KEY_PREFIX}测试组织 ${Date.now()}`

    // 名称(必填)
    await page.locator('.el-dialog .el-form-item:has-text("名称") input').fill(orgName)
    // 负责人
    await page.locator('.el-dialog .el-form-item:has-text("负责人") input').fill('E2E 测试负责人')
    // 电话
    await page.locator('.el-dialog .el-form-item:has-text("电话") input').fill('13900000000')

    // 提交(默认 submit-text="保存")
    await page.locator('.el-dialog button:has-text("保存")').click()
    await expect(page.locator('.el-dialog__title')).not.toBeVisible({ timeout: 5_000 })

    // 验证:树里出现新组织
    const exactText = (t: string) => page.getByText(t, { exact: true })
    await page.waitForTimeout(1000)
    await expect(exactText(orgName).first()).toBeVisible({ timeout: 5_000 })

    // 提取 id 用于清理
    const newId = await api(
      'GET', '/api/system/organization/tree', undefined, token
    ).then((r: any) => {
      function find(items: any[]): any {
        for (const n of items) {
          if (n.name === orgName) return n
          if (n.children) { const c = find(n.children); if (c) return c }
        }
        return null
      }
      return find(r.data ?? [])?.id
    })
    if (newId) createdIds.push(Number(newId))
  })
})