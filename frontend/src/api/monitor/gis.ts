import request from '@/api/request'

export interface GisDeviceVO {
  deviceId: number
  deviceKey: string
  deviceName: string
  lng: number
  lat: number
  status: number        // 0=离线 1=在线 2=禁用
  address?: string
  alertCount?: number
}

export function listGisDevices() {
  return request<GisDeviceVO[]>({ url: '/monitor/gis/devices', method: 'get' })
}