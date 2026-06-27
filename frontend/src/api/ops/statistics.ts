import request from '@/api/request'

export interface OpsKpiVO {
  period: string          // 'YYYY-MM' 或 'YYYY-MM-DD'
  kpiType: string
  value: number
  group?: string
  rank?: number
}

export interface OpsKpiSummary {
  slaRate: number         // %
  avgResponseMin: number  // 平均响应时长(分钟)
  faultRate: number       // 故障率 %
  totalWorkOrders: number
  totalAlerts: number
}

export function getOpsKpiSummary(range: '7d' | '30d' | '90d' = '30d') {
  return request<OpsKpiSummary>({
    url: '/ops/statistics/summary',
    method: 'get',
    params: { range }
  })
}

export function getOpsKpiTrend(kpiType: string, range: '7d' | '30d' | '90d' = '30d') {
  return request<OpsKpiVO[]>({
    url: '/ops/statistics/trend',
    method: 'get',
    params: { kpiType, range }
  })
}

export function getGroupRank(kpiType: string) {
  return request<OpsKpiVO[]>({
    url: '/ops/statistics/group-rank',
    method: 'get',
    params: { kpiType }
  })
}

export function getFaultTypeDistribution(range: '7d' | '30d' | '90d' = '30d') {
  return request<{ type: string; count: number }[]>({
    url: '/ops/statistics/fault-type',
    method: 'get',
    params: { range }
  })
}