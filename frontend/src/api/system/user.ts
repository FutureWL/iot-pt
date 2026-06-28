import request from '@/api/request'
import type { PageQuery } from '@/types/common'
import { adaptCrudPage, adaptCrudRemove } from '@/api/crud'
import type { SysRoleVO } from './role'
export type { SysRoleVO } from './role'

export interface SysUserVO {
  id: number
  tenantId: number
  username: string
  nickname?: string
  avatar?: string
  email?: string
  phone?: string
  status: number
  lastLoginAt?: string
  createdAt?: string
  updatedAt?: string
}

export interface UserDTO {
  id?: number
  username: string
  password?: string
  nickname?: string
  email?: string
  phone?: string
  status?: number
}

export interface UserQuery extends PageQuery {
  keyword?: string
  status?: number
}

/** 分页列表 (后端 records 适配为前端 list) */
export function pageUsers(params: UserQuery) {
  return request<{
    records: SysUserVO[]
    total: number
    size: number
    current: number
    pages: number
  }>({
    url: '/system/user/page',
    method: 'get',
    params
  })
}

/** 详情 */
export function getUser(id: number) {
  return request<SysUserVO>({ url: `/system/user/${id}`, method: 'get' })
}

/** 新建 */
export function createUser(data: UserDTO) {
  return request<void>({ url: '/system/user', method: 'post', data })
}

/** 更新 */
export function updateUser(data: UserDTO) {
  return request<void>({ url: '/system/user', method: 'put', data })
}

/** 删除 */
export function deleteUser(id: number) {
  return request<void>({ url: `/system/user/${id}`, method: 'delete' })
}

/** 重置密码 */
export function resetPassword(id: number, newPassword: string) {
  return request<void>({
    url: `/system/user/${id}/reset-password`,
    method: 'post',
    data: { newPassword }
  })
}

/** 启/停 */
export function toggleStatus(id: number, status: number) {
  return request<void>({
    url: `/system/user/${id}/status/${status}`,
    method: 'put'
  })
}

// 角色 API 透传
export { allRoles } from './role'

/** CrudList 适配 */
export const userCrud = {
  page: adaptCrudPage<SysUserVO, UserQuery>(pageUsers),
  remove: adaptCrudRemove<SysUserVO>(deleteUser)
}
