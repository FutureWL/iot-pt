import request from '@/api/request'
import type { PageQuery } from '@/types/common'
import { asCrudApi, type CrudApi } from '@/api/crud'

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

export function getKnowledgeDetail(id: number) {
  return request<KnowledgeDetailVO>({ url: `/knowledge/${id}`, method: 'get' })
}

export function createKnowledge(data: Partial<KnowledgeDetailVO>) {
  return request<{ id: number }>({ url: '/knowledge', method: 'post', data })
}

export function updateKnowledge(data: Partial<KnowledgeDetailVO>) {
  return request<void>({ url: '/knowledge', method: 'put', data })
}

export function deleteKnowledge(id: number) {
  return request<void>({ url: `/knowledge/${id}`, method: 'delete' })
}

/** CrudList 适配:业务侧 import 后直接 :api="knowledgeCrud" */
export const knowledgeCrud = asCrudApi<KnowledgeVO, KnowledgeQuery>({
  page: async (q) => {
    // pageKnowledge 返回 ApiResponse 包装,CrudApi 需要 PageResult
    const res: any = await pageKnowledge(q)
    const data = res.data ?? {}
    return {
      records: data.records ?? [],
      total: data.total ?? 0,
      size: data.size ?? q.pageSize ?? 10,
      current: data.current ?? q.pageNum ?? 1,
      pages: data.pages ?? Math.ceil((data.total ?? 0) / (data.size ?? q.pageSize ?? 10))
    }
  },
  // CrudApi.remove 是 (id: string | number),deleteKnowledge 是 (id: number),用 cast
  remove: ((id: string | number) => deleteKnowledge(id as number)) as CrudApi<
    KnowledgeVO,
    KnowledgeQuery
  >['remove']
})
