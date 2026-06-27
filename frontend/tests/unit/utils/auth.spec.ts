/**
 * utils/auth.ts 单元测试
 *
 * 验证 cookie 的 get/set/remove 与 tenantId 工具函数
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import Cookies from 'js-cookie'
import { getToken, setToken, removeToken, getTenantId, setTenantId } from '@/utils/auth'

describe('utils/auth.ts', () => {
  beforeEach(() => {
    vi.mocked(Cookies.get as any).mockReset()
    vi.mocked(Cookies.set as any).mockReset()
    vi.mocked(Cookies.remove as any).mockReset()
  })

  describe('getToken / setToken / removeToken', () => {
    it('setToken 写入 cookie 时使用 iot_token 作为 key,7 天过期', () => {
      setToken('abc123')
      expect(Cookies.set as any).toHaveBeenCalledWith('iot_token', 'abc123', { expires: 7 })
    })

    it('getToken 直接读取 cookie 中的 iot_token', () => {
      vi.mocked(Cookies.get as any).mockReturnValue('abc123')
      expect(getToken()).toBe('abc123')
      expect(Cookies.get as any).toHaveBeenCalledWith('iot_token')
    })

    it('getToken 在 cookie 无值时返回 undefined', () => {
      vi.mocked(Cookies.get as any).mockReturnValue(undefined)
      expect(getToken()).toBeUndefined()
    })

    it('removeToken 同时清除 token 和 tenant', () => {
      removeToken()
      expect(Cookies.remove as any).toHaveBeenCalledWith('iot_token')
      expect(Cookies.remove as any).toHaveBeenCalledWith('iot_tenant_id')
    })
  })

  describe('getTenantId / setTenantId', () => {
    it('setTenantId 写入数字转字符串,7 天过期', () => {
      setTenantId(100)
      expect(Cookies.set as any).toHaveBeenCalledWith('iot_tenant_id', '100', { expires: 7 })
    })

    it('getTenantId 在有值时返回数字', () => {
      vi.mocked(Cookies.get as any).mockReturnValue('200')
      expect(getTenantId()).toBe(200)
    })

    it('getTenantId 在无值时返回 undefined', () => {
      vi.mocked(Cookies.get as any).mockReturnValue(undefined)
      expect(getTenantId()).toBeUndefined()
    })

    it('getTenantId 接受字符串数字', () => {
      vi.mocked(Cookies.get as any).mockReturnValue('42')
      expect(getTenantId()).toBe(42)
    })
  })
})
