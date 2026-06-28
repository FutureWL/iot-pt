import request from '@/api/request'
import type { PageQuery } from '@/types/common'
import { adaptCrudPage, adaptCrudRemove } from '@/api/crud'

export interface IotProductVO {
  id: number
  tenantId: number
  productKey: string
  productName: string
  category?: string
  description?: string
  authType: string         // deviceSecret / dynamic / none
  nodeType: number         // 0=直连 1=网关 2=网关子设备
  netType: string          // MQTT / TCP
  status: number
  icon?: string
  thingModel: string       // JSON 字符串
  createdAt?: string
  updatedAt?: string
}

export interface IotProductDTO {
  id?: number
  productKey: string
  productName: string
  category?: string
  description?: string
  authType: string
  nodeType?: number
  netType: string
  status?: number
  icon?: string
  thingModel?: string
}

export interface IotProductQuery extends PageQuery {
  keyword?: string
  category?: string
  netType?: string
  status?: number
}

export function pageProducts(params: IotProductQuery) {
  return request<{
    records: IotProductVO[]
    total: number
    size: number
    current: number
    pages: number
  }>({ url: '/iot/product/page', method: 'get', params })
}

export function allProducts() {
  return request<IotProductVO[]>({ url: '/iot/product/all', method: 'get' })
}

export function defaultThingModel() {
  return request<string>({ url: '/iot/product/thing-model/default', method: 'get' })
}

export function getProduct(id: number) {
  return request<IotProductVO>({ url: `/iot/product/${id}`, method: 'get' })
}

export function createProduct(data: IotProductDTO) {
  return request<void>({ url: '/iot/product', method: 'post', data })
}

export function updateProduct(data: IotProductDTO) {
  return request<void>({ url: '/iot/product', method: 'put', data })
}

export function deleteProduct(id: number) {
  return request<void>({ url: `/iot/product/${id}`, method: 'delete' })
}

/** CrudList 适配 */
export const productCrud = {
  page: adaptCrudPage<IotProductVO, IotProductQuery>(pageProducts),
  remove: adaptCrudRemove<IotProductVO>(deleteProduct)
}