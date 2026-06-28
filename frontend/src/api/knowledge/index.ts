import request from '@/api/request'
import type { PageQuery } from '@/types/common'
import { adaptCrudPage, adaptCrudRemove } from '@/api/crud'

export interface KnowledgeVO {
  id: number
  category: string
  title: string
  summary?: string
  tags?: string
  version: number
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
  author: string
  updatedAt: string
}

export interface KnowledgeDetailVO extends KnowledgeVO {
  content: string
}

export interface KnowledgeQuery extends PageQuery {
  category?: string
  status?: string
}

export function pageKnowledge(params: KnowledgeQuery) {
  return request<{
    records: KnowledgeVO[]
    total: number
    size: number
    current: number
  }>({ url: '/knowledge/page', method: 'get', params })
}

/** 接受 string|number:Snowflake id 是 19 位 long,Number() 会丢精度,路由参数应用 string */
export function getKnowledgeDetail(id: string | number) {
  return request<KnowledgeDetailVO>({ url: `/knowledge/${id}`, method: 'get' })
}

export function createKnowledge(data: Partial<KnowledgeDetailVO>) {
  return request<{ id: number }>({ url: '/knowledge', method: 'post', data })
}

export function updateKnowledge(data: Partial<KnowledgeDetailVO>) {
  return request<void>({ url: '/knowledge', method: 'put', data })
}

export function deleteKnowledge(id: string | number) {
  return request<void>({ url: `/knowledge/${id}`, method: 'delete' })
}

/** CrudList 适配:业务侧 import 后直接 :api="knowledgeCrud" */
export const knowledgeCrud = {
  page: adaptCrudPage<KnowledgeVO, KnowledgeQuery>(pageKnowledge),
  remove: adaptCrudRemove<KnowledgeVO>(deleteKnowledge)
}
