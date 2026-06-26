import Cookies from 'js-cookie'

const TOKEN_KEY = 'iot_token'
const TENANT_KEY = 'iot_tenant_id'

export function getToken(): string | undefined {
  return Cookies.get(TOKEN_KEY)
}

export function setToken(token: string, expires = 7) {
  Cookies.set(TOKEN_KEY, token, { expires })
}

export function removeToken() {
  Cookies.remove(TOKEN_KEY)
  Cookies.remove(TENANT_KEY)
}

export function getTenantId(): string | number | undefined {
  const v = Cookies.get(TENANT_KEY)
  return v ? Number(v) : undefined
}

export function setTenantId(id: string | number) {
  Cookies.set(TENANT_KEY, String(id), { expires: 7 })
}
