import request from './request'

export interface LoginParams {
  username: string
  password: string
  tenantCode?: string
}

export interface LoginResult {
  token: string
  userId: number
  username: string
  nickname: string
  tenantId: number
  tenantCode: string
  roles: string[]
  permissions: string[]
}

export function login(data: LoginParams) {
  return request<LoginResult>({
    url: '/auth/login',
    method: 'post',
    data
  })
}

export function logout() {
  return request<void>({ url: '/auth/logout', method: 'post' })
}

export function getUserInfo() {
  return request<LoginResult>({ url: '/auth/info', method: 'get' })
}
