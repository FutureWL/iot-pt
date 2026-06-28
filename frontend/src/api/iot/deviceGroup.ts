import request from '@/api/request'
import type { PageQuery } from '@/types/common'
import { adaptCrudPage, adaptCrudRemove } from '@/api/crud'

export interface IotDeviceGroupVO {
  id: number
  tenantId: number
  parentId: number
  groupName: string
  description?: string
  sort: number
  deviceCount: number
  createdAt?: string
}

export interface IotDeviceGroupDTO {
  id?: number
  parentId?: number
  groupName: string
  description?: string
  sort?: number
}

export interface IotDeviceGroupQuery extends PageQuery {
  keyword?: string
}

export function allGroups() {
  return request<IotDeviceGroupVO[]>({ url: '/iot/device-group/all', method: 'get' })
}

export function pageGroups(params: IotDeviceGroupQuery) {
  return request<{
    records: IotDeviceGroupVO[]
    total: number
    size: number
    current: number
    pages: number
  }>({ url: '/iot/device-group/page', method: 'get', params })
}

export function createGroup(data: IotDeviceGroupDTO) {
  return request<void>({ url: '/iot/device-group', method: 'post', data })
}

export function updateGroup(data: IotDeviceGroupDTO) {
  return request<void>({ url: '/iot/device-group', method: 'put', data })
}

export function deleteGroup(id: number) {
  return request<void>({ url: `/iot/device-group/${id}`, method: 'delete' })
}

/** CrudList 适配 */
export const groupCrud = {
  page: adaptCrudPage<IotDeviceGroupVO, IotDeviceGroupQuery>(pageGroups),
  remove: adaptCrudRemove<IotDeviceGroupVO>(deleteGroup)
}