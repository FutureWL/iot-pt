import request from '@/api/request'
import type { PageQuery } from '@/types/common'

export interface IotRuleVO {
  id: number
  tenantId: number
  ruleName: string
  description?: string
  triggerType: string         // data / property / event / online / offline
  filterExpr: string          // JSON
  actions: string             // JSON
  status: number
  createdAt?: string
  updatedAt?: string
}

export interface IotRuleDTO {
  id?: number
  ruleName: string
  description?: string
  triggerType: string
  filterExpr: string
  actions: string
  status?: number
}

export interface IotRuleQuery extends PageQuery {
  keyword?: string
  triggerType?: string
  status?: number
}

export function pageRules(params: IotRuleQuery) {
  return request<{
    records: IotRuleVO[]
    total: number
    size: number
    current: number
    pages: number
  }>({ url: '/rule/page', method: 'get', params })
}

export function getRule(id: number) {
  return request<IotRuleVO>({ url: `/rule/${id}`, method: 'get' })
}

export function createRule(data: IotRuleDTO) {
  return request<void>({ url: '/rule', method: 'post', data })
}

export function updateRule(data: IotRuleDTO) {
  return request<void>({ url: '/rule', method: 'put', data })
}

export function deleteRule(id: number) {
  return request<void>({ url: `/rule/${id}`, method: 'delete' })
}

export function toggleRule(id: number, status: number) {
  return request<void>({ url: `/rule/${id}/status/${status}`, method: 'put' })
}