/**
 * 组织架构 — 真集成 E2E
 *
 * 已知 gap (2026-06-29): 后端 SysOrganizationController 是 stub:
 *   - 仅 GET /system/organization/tree
 *   - POST / PUT / DELETE 端点缺失 → 500 "No static resource"
 *
 * 完整实现需要:
 *   - SysOrganization entity + mapper
 *   - SysOrganizationService 4 个方法(tree/create/update/delete)
 *   - SysOrganizationController 对应 4 个端点
 *
 * 当前 spec 只测:
 *   1. 列表页加载(tree 渲染,可能为空)
 *   2. 树节点 CRUD — 标 SKIP,等后端补完
 */
import { test, expect } from '@playwright/test'
import { loginAsAdmin, REAL_BACKEND, waitForBackend } from './_helpers'

const TEST_TIMEOUT = 30_000

test.describe('组织架构 真集成', () => {
  test.beforeEach(async ({ context }) => {
    test.setTimeout(TEST_TIMEOUT)
    if (!REAL_BACKEND) test.skip(true, 'E2E_REAL_BACKEND=false, skip real backend test')
    const ready = await waitForBackend()
    if (!ready) test.skip(true, '后端未就绪, 跳过')
    await loginAsAdmin(context)
  })

  test('列表页加载 + 树渲染', async ({ page }) => {
    await page.goto('/#/system/organization')
    await page.waitForLoadState('networkidle')
    await expect(page.locator('.page-title')).toContainText('组织', { timeout: 5_000 })

    // 等树或空状态
    await page.waitForTimeout(800)

    // 新建按钮存在
    await expect(page.locator('button:has-text("新建顶级组织")')).toBeVisible()

    // 树或 el-empty 都算正常
    const tree = page.locator('.el-tree')
    const empty = page.locator('.el-empty')
    const treeCount = await tree.count()
    const emptyCount = await empty.count()
    expect(treeCount + emptyCount).toBeGreaterThan(0)
  })

  test('CRUD — 需后端补完 (当前 SKIP)', () => {
    // 后端 organization CRUD 未实现
    test.skip(true, '后端 SysOrganizationController 缺 POST/PUT/DELETE — 已记入 TODO')
  })
})