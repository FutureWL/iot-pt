import request from '@/api/request'

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

export function allGroups() {
  return request<IotDeviceGroupVO[]>({ url: '/iot/device-group/all', method: 'get' })
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