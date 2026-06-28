import request from '@/api/request'

export interface SysOrganizationVO {
  id: number
  parentId: number
  name: string
  path: string             // 物化路径 /a/b/c
  sort: number
  leader?: string
  phone?: string
  description?: string
  children?: SysOrganizationVO[]
  createdAt?: string
}

export function treeOrganizations() {
  return request<SysOrganizationVO[]>({ url: '/system/organization/tree', method: 'get' })
}

export function createOrganization(data: Partial<SysOrganizationVO>) {
  return request<{ id: number }>({ url: '/system/organization', method: 'post', data })
}

export function updateOrganization(data: Partial<SysOrganizationVO>) {
  return request<void>({ url: '/system/organization', method: 'put', data })
}

export function deleteOrganization(id: string | number) {
  return request<void>({ url: `/system/organization/${id}`, method: 'delete' })
}