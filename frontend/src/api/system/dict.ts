import request from '@/api/request'
import type { PageQuery } from '@/types/common'
import { adaptCrudPage, adaptCrudRemove } from '@/api/crud'

export interface SysDictVO {
  id: number
  type: string        // 字典类型编码
  code: string        // 字典编码
  label: string       // 显示名
  value: string       // 字典值
  sort: number
  description?: string
  status: number      // 0=禁用 1=启用
}

export interface SysDictTypeVO {
  id: number
  type: string
  typeName: string
  description?: string
  status: number
}

export interface DictQuery extends PageQuery {
  type?: string
  status?: number
}

export function pageDictTypes(params: PageQuery) {
  return request<{
    records: SysDictTypeVO[]
    total: number
  }>({ url: '/system/dict/type/page', method: 'get', params })
}

export function pageDictItems(params: DictQuery) {
  return request<{
    records: SysDictVO[]
    total: number
  }>({ url: '/system/dict/item/page', method: 'get', params })
}

export function createDictType(data: Partial<SysDictTypeVO>) {
  return request<{ id: number }>({ url: '/system/dict/type', method: 'post', data })
}

export function createDictItem(data: Partial<SysDictVO>) {
  return request<{ id: number }>({ url: '/system/dict/item', method: 'post', data })
}

export function updateDictItem(data: Partial<SysDictVO>) {
  return request<void>({ url: '/system/dict/item', method: 'put', data })
}

export function deleteDictItem(id: string | number) {
  return request<void>({ url: `/system/dict/item/${id}`, method: 'delete' })
}

export function deleteDictType(id: string | number) {
  return request<void>({ url: `/system/dict/type/${id}`, method: 'delete' })
}

/** CrudList 适配 — 字典项 */
export const dictItemCrud = {
  page: adaptCrudPage<SysDictVO, DictQuery>(pageDictItems),
  remove: adaptCrudRemove<SysDictVO>(deleteDictItem)
}