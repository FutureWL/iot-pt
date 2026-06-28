import request from '@/api/request'

export type ReportFormat = 'PDF' | 'EXCEL' | 'WORD'
export type ReportType = '巡检日报' | '故障分析' | '运行总结' | '告警汇总'

export interface ReportTemplateVO {
  id: number
  templateName: string
  reportType: ReportType
  description?: string
  paramsSchema: string     // JSON Schema 字符串
}

export interface ReportGenerateParams {
  templateId: number
  deviceId?: number
  startTime?: string
  endTime?: string
  format: ReportFormat
}

/** 报表模板列表 */
export function listReportTemplates() {
  return request<ReportTemplateVO[]>({ url: '/report/templates', method: 'get' })
}

/** 历史报表 */
export function pageGeneratedReports(params: { pageNum: number; pageSize: number }) {
  return request<{
    records: {
      id: number
      templateName: string
      generatedAt: string
      format: ReportFormat
      size: number
      url: string
    }[]
    total: number
  }>({ url: '/report/generated/page', method: 'get', params })
}

/** 触发报表生成 */
export function generateReport(params: ReportGenerateParams) {
  return request<{ taskId: string }>({ url: '/report/generate', method: 'post', data: params })
}

/** 下载报表 */
export function downloadReport(id: string | number) {
  return request<Blob>({
    url: `/report/${id}/download`,
    method: 'get',
    responseType: 'blob'
  })
}