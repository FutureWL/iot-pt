import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as apiLogin, logout as apiLogout, getUserInfo, type LoginParams, type LoginResult } from '@/api/auth'
import { getToken, setToken, removeToken, setTenantId } from '@/utils/auth'

export const useUserStore = defineStore('user', () => {
  // 页面刷新时从 cookie 恢复 token，避免登录态丢失
  const token = ref<string>(getToken() ?? '')
  const userInfo = ref<LoginResult | null>(null)

  const isLoggedIn = computed(() => !!token.value)
  const permissions = computed(() => userInfo.value?.permissions ?? [])
  const roles = computed(() => userInfo.value?.roles ?? [])

  async function login(params: LoginParams) {
    const res: any = await apiLogin(params)
    token.value = res.data.token
    userInfo.value = res.data
    setToken(res.data.token)
    setTenantId(res.data.tenantId)
    return res.data
  }

  async function fetchUserInfo() {
    const res: any = await getUserInfo()
    userInfo.value = res.data
    return res.data
  }

  async function logout() {
    try {
      await apiLogout()
    } catch {}
    reset()
  }

  function reset() {
    token.value = ''
    userInfo.value = null
    removeToken()
  }

  function hasPermission(p: string): boolean {
    if (!p) return true
    if (roles.value.includes('SUPER_ADMIN')) return true
    return permissions.value.includes(p)
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    permissions,
    roles,
    login,
    fetchUserInfo,
    logout,
    reset,
    hasPermission
  }
})
