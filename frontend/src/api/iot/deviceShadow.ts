import request from '@/api/request'

export interface IotDeviceShadowVO {
  id?: number
  deviceId: number
  identifier: string
  name?: string
  type?: string
  unit?: string
  accessMode?: string
  valueJson?: string
  updatedAt?: string
}

export interface IotDeviceShadowDTO {
  productId: number
  identifier: string
  value: any
}

export function getDeviceShadows(deviceId: number) {
  return request<IotDeviceShadowVO[]>({
    url: `/iot/device-shadow/${deviceId}`,
    method: 'get'
  })
}

export function upsertDeviceShadow(deviceId: number, data: IotDeviceShadowDTO) {
  return request<void>({
    url: `/iot/device-shadow/${deviceId}`,
    method: 'post',
    data
  })
}

export function deleteDeviceShadow(deviceId: number, identifier: string) {
  return request<void>({
    url: `/iot/device-shadow/${deviceId}/${identifier}`,
    method: 'delete'
  })
}