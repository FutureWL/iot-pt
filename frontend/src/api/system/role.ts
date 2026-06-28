import request from '@/api/request'
import type { PageQuery } from '@/types/common'
import { adaptCrudPage, adaptCrudRemove } from '@/api/crud'

export interface SysRoleVO {
  id: number
  tenantId: number
  roleCode: string
  roleName: string
  description?: string
  builtIn: number
  menuIds?: number[]
  createdAt?: string
  updatedAt?: string
}

export interface RoleDTO {
  id?: number
  roleCode: string
  roleName: string
  description?: string
}

export interface RoleQuery extends PageQuery {
  keyword?: string
}

export function pageRoles(params: RoleQuery) {
  return request<{
    records: SysRoleVO[]
    total: number
    size: number
    current: number
    pages: number
  }>({ url: '/system/role/page', method: 'get', params })
}

export function allRoles() {
  return request<SysRoleVO[]>({ url: '/system/role/all', method: 'get' })
}

export function getRole(id: number) {
  return request<SysRoleVO>({ url: `/system/role/${id}`, method: 'get' })
}

export function createRole(data: RoleDTO) {
  return request<void>({ url: '/system/role', method: 'post', data })
}

export function updateRole(data: RoleDTO) {
  return request<void>({ url: '/system/role', method: 'put', data })
}

export function deleteRole(id: number) {
  return request<void>({ url: `/system/role/${id}`, method: 'delete' })
}

/** CrudList 适配 */
export const roleCrud = {
  page: adaptCrudPage<SysRoleVO, RoleQuery>(pageRoles),
  remove: adaptCrudRemove<SysRoleVO>(deleteRole)
}

export function getRoleMenuIds(id: number) {
  return request<number[]>({ url: `/system/role/${id}/menus`, method: 'get' })
}

export function assignRoleMenus(id: number, menuIds: number[]) {
  return request<void>({
    url: `/system/role/${id}/menus`,
    method: 'put',
    data: { menuIds }
  })
}

/** 用户-角色绑定 */
export function getUserRoleIds(userId: number) {
  return request<number[]>({ url: `/system/user-role/${userId}`, method: 'get' })
}

export function assignUserRoles(userId: number, roleIds: number[]) {
  return request<void>({
    url: `/system/user-role/${userId}`,
    method: 'put',
    data: { roleIds }
  })
}