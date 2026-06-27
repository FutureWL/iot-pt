/**
 * user store 单元测试
 *
 * 覆盖以下场景:
 *   1. 初始化时从 cookie 恢复 token(修复刷新后丢失登录态的 bug)
 *   2. 登录后正确写入 token 到 cookie
 *   3. 登出后清除 cookie 中的 token
 *   4. hasPermission 权限校验
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import Cookies from 'js-cookie'
import { useUserStore } from '@/stores/user'

// mock API,避免真实网络请求
vi.mock('@/api/auth', () => ({
  login: vi.fn(),
  logout: vi.fn(),
  getUserInfo: vi.fn()
}))

import { login as apiLogin, logout as apiLogout, getUserInfo } from '@/api/auth'

describe('stores/user.ts', () => {
  beforeEach(() => {
    // 每个测试用新的 pinia 实例,互不影响
    setActivePinia(createPinia())
    // 重置 cookie 内存 mock
    vi.mocked(Cookies.get as any).mockReset()
    vi.mocked(Cookies.set as any).mockReset()
    vi.mocked(Cookies.remove as any).mockReset()
  })

  describe('初始化 (修复刷新后丢失登录态 bug)', () => {
    it('当 cookie 中存在 token 时,store 应正确恢复', () => {
      // 模拟刷新前 cookie 中存有 token
      vi.mocked(Cookies.get as any).mockImplementation((key: string) => {
        if (key === 'iot_token') return 'persisted-jwt-token'
        return undefined
      })

      const store = useUserStore()

      expect(store.token).toBe('persisted-jwt-token')
      expect(store.isLoggedIn).toBe(true)
    })

    it('当 cookie 中无 token 时,store 应初始化为空', () => {
      vi.mocked(Cookies.get as any).mockReturnValue(undefined)

      const store = useUserStore()

      expect(store.token).toBe('')
      expect(store.isLoggedIn).toBe(false)
    })

    it('getToken 被正确以 iot_token 为 key 调用', () => {
      vi.mocked(Cookies.get as any).mockReturnValue(undefined)
      useUserStore()
      expect(Cookies.get as any).toHaveBeenCalledWith('iot_token')
    })
  })

  describe('login()', () => {
    it('登录成功后 token 和 userInfo 都被设置,cookie 同步', async () => {
      vi.mocked(Cookies.get as any).mockReturnValue(undefined)
      vi.mocked(apiLogin as any).mockResolvedValue({
        data: {
          token: 'new-jwt-token',
          userId: 1,
          username: 'admin',
          nickname: '管理员',
          tenantId: 100,
          tenantCode: 'default',
          roles: ['SUPER_ADMIN'],
          permissions: ['*']
        }
      })

      const store = useUserStore()
      const result = await store.login({ username: 'admin', password: '123456' })

      expect(result.token).toBe('new-jwt-token')
      expect(store.token).toBe('new-jwt-token')
      expect(store.isLoggedIn).toBe(true)
      expect(store.userInfo?.username).toBe('admin')
      // cookie 持久化
      expect(Cookies.set as any).toHaveBeenCalledWith('iot_token', 'new-jwt-token', { expires: 7 })
    })
  })

  describe('logout()', () => {
    it('登出后 token 被清空,cookie 中 token 也被删除', async () => {
      vi.mocked(Cookies.get as any).mockReturnValue('existing-token')
      vi.mocked(apiLogout).mockResolvedValue(undefined as any)

      const store = useUserStore()
      expect(store.isLoggedIn).toBe(true)

      await store.logout()

      expect(store.token).toBe('')
      expect(store.isLoggedIn).toBe(false)
      expect(store.userInfo).toBeNull()
      expect(Cookies.remove as any).toHaveBeenCalledWith('iot_token')
    })
  })

  describe('hasPermission()', () => {
    function buildStore(roles: string[], permissions: string[]) {
      const store = useUserStore()
      store.userInfo = {
        userId: 1,
        username: 'u',
        nickname: 'n',
        tenantId: 1,
        tenantCode: 'd',
        token: 't',
        roles,
        permissions
      } as any
      return store
    }

    it('空字符串权限默认放行', () => {
      const store = buildStore([], [])
      expect(store.hasPermission('')).toBe(true)
    })

    it('SUPER_ADMIN 角色拥有所有权限', () => {
      const store = buildStore(['SUPER_ADMIN'], [])
      expect(store.hasPermission('any:permission')).toBe(true)
    })

    it('普通角色按 permissions 列表校验', () => {
      const store = buildStore(['USER'], ['device:list', 'device:edit'])
      expect(store.hasPermission('device:list')).toBe(true)
      expect(store.hasPermission('device:delete')).toBe(false)
    })
  })
})
