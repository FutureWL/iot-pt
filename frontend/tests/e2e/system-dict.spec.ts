/**
 * 字典管理 — 真集成 E2E
 *
 * 已知 gap (2026-06-29): 后端 SysDictController / SysDictService 是 stub 实现:
 *   - 仅 GET /system/dict/type/page(且 pageTypes 返回空,没接 DB)
 *   - POST /system/dict/type 缺失 → 500 "No static resource"
 *   - 所有 item / create / update / delete 端点缺失
 *
 * 完整实现需要:
 *   - V3 migration: sys_dict_type + sys_dict_item 表
 *   - SysDictType / SysDictItem entity + mapper
 *   - SysDictService 7 个方法(pageTypes/createType/updateType/deleteType +
 *     pageItems/createItem/updateItem/deleteItem)
 *   - SysDictController 对应 7 个端点
 *
 * 当前 spec 只测:
 *   1. 列表页能加载(真后端,即使是空)
 *   2. 完整双层 CRUD — 标 SKIP,等后端补完
 */
import { test, expect } from '@playwright/test'
import { loginAsAdmin, REAL_BACKEND, waitForBackend } from './_helpers'

const TEST_TIMEOUT = 30_000

test.describe('字典管理 真集成', () => {
  test.beforeEach(async ({ context }) => {
    test.setTimeout(TEST_TIMEOUT)
    if (!REAL_BACKEND) test.skip(true, 'E2E_REAL_BACKEND=false, skip real backend test')
    const ready = await waitForBackend()
    if (!ready) test.skip(true, '后端未就绪, 跳过')
    await loginAsAdmin(context)
  })

  test('列表页加载', async ({ page }) => {
    await page.goto('/#/system/dict')
    await page.waitForLoadState('networkidle')
    await expect(page.locator('.page-title')).toContainText('字典', { timeout: 5_000 })
    // 等待左右两侧骨架渲染
    await page.waitForTimeout(500)
    // 新建按钮存在
    await expect(page.locator('button:has-text("新建字典类型")')).toBeVisible()
  })

  test('双层 CRUD — 需后端补完 (当前 SKIP)', async () => {
    // 后端 SysDictController 缺失 create / item 端点
    // 等待 V3 migration + entity/mapper/service 补完后再启用
    test.skip(true, '后端 dict CRUD 未实现, 需 V3 migration + 7 个 service/endpoint — 已记入 TODO')
  })
})