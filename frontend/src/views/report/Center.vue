<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh, Document, Download, DocumentCopy } from '@element-plus/icons-vue'
import {
  listReportTemplates,
  pageGeneratedReports,
  generateReport,
  downloadReport,
  type ReportTemplateVO,
  type ReportFormat
} from '@/api/report'

const loading = ref(false)
const templates = ref<ReportTemplateVO[]>([])
const selectedTemplate = ref<ReportTemplateVO | null>(null)

const params = reactive({
  startTime: '',
  endTime: '',
  format: 'PDF' as ReportFormat,
  deviceKey: ''
})

const query = reactive({ pageNum: 1, pageSize: 10 })
const history = ref<any[]>([])
const total = ref(0)

const formatOptions: { value: ReportFormat; label: string }[] = [
  { value: 'PDF', label: 'PDF' },
  { value: 'EXCEL', label: 'Excel' },
  { value: 'WORD', label: 'Word' }
]

async function load() {
  loading.value = true
  try {
    const [tRes, hRes]: any[] = await Promise.all([
      listReportTemplates(),
      pageGeneratedReports(query)
    ])
    templates.value = tRes.data ?? []
    history.value = hRes.data?.records ?? []
    total.value = hRes.data?.total ?? 0
  } finally {
    loading.value = false
  }
}

function selectTemplate(t: ReportTemplateVO) {
  selectedTemplate.value = t
}

async function onGenerate() {
  if (!selectedTemplate.value) {
    ElMessage.warning('请先选择报表模板')
    return
  }
  try {
    await generateReport({
      templateId: selectedTemplate.value.id,
      startTime: params.startTime || undefined,
      endTime: params.endTime || undefined,
      format: params.format
    })
    ElMessage.success('报表生成任务已提交,稍候请到下方"历史报表"列表下载')
    load()
  } catch {}
}

async function onDownload(row: any) {
  try {
    const res: any = await downloadReport(row.id)
    const blob = new Blob([res.data ?? res], { type: 'application/octet-stream' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${row.templateName}.${row.format.toLowerCase()}`
    a.click()
    URL.revokeObjectURL(url)
  } catch {}
}

onMounted(load)
</script>

<template>
  <div class="page-container report-page" v-loading="loading">
    <div class="page-header">
      <h2 class="page-title">报表中心</h2>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <el-row :gutter="16">
      <!-- 左:模板库 -->
      <el-col :xs="24" :md="10">
        <div class="page-card">
          <h3 class="card-title"><el-icon><Document /></el-icon> 报表模板库</h3>
          <div class="template-list">
            <div v-for="t in templates" :key="t.id"
              class="template-item" :class="{ active: selectedTemplate?.id === t.id }"
              @click="selectTemplate(t)">
              <div class="tpl-name">
                <el-icon><DocumentCopy /></el-icon>
                {{ t.templateName }}
              </div>
              <el-tag size="small" type="info">{{ t.reportType }}</el-tag>
              <div class="tpl-desc text-secondary text-xs">{{ t.description || '—' }}</div>
            </div>
            <el-empty v-if="templates.length === 0" description="暂无模板" />
          </div>
        </div>
      </el-col>

      <!-- 右:参数配置 -->
      <el-col :xs="24" :md="14">
        <div class="page-card mb-16">
          <h3 class="card-title">报表参数配置</h3>
          <el-form label-width="100px">
            <el-form-item label="已选模板">
              <span v-if="selectedTemplate" class="text-primary">{{ selectedTemplate.templateName }}</span>
              <span v-else class="text-placeholder">请在左侧选择模板</span>
            </el-form-item>
            <el-form-item label="设备 Key">
              <el-input v-model="params.deviceKey" placeholder="可选:过滤特定设备,留空表示全部" />
            </el-form-item>
            <el-form-item label="时间范围">
              <el-date-picker
                v-model="params.startTime"
                type="datetime"
                placeholder="开始时间"
                style="width: 45%"
                value-format="YYYY-MM-DD HH:mm:ss" />
              <span style="margin: 0 8px">至</span>
              <el-date-picker
                v-model="params.endTime"
                type="datetime"
                placeholder="结束时间"
                style="width: 45%"
                value-format="YYYY-MM-DD HH:mm:ss" />
            </el-form-item>
            <el-form-item label="输出格式">
              <el-radio-group v-model="params.format">
                <el-radio-button v-for="f in formatOptions" :key="f.value" :value="f.value">
                  {{ f.label }}
                </el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Document" :disabled="!selectedTemplate" @click="onGenerate">
                生成报表
              </el-button>
            </el-form-item>
          </el-form>
        </div>

        <div class="page-card">
          <h3 class="card-title">历史报表</h3>
          <el-table :data="history" stripe empty-text="暂无历史报表">
            <el-table-column prop="templateName" label="报表名" min-width="200" />
            <el-table-column prop="format" label="格式" width="80">
              <template #default="{ row }">
                <el-tag size="small">{{ row.format }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="size" label="大小" width="100">
              <template #default="{ row }">{{ (row.size / 1024).toFixed(1) }} KB</template>
            </el-table-column>
            <el-table-column prop="generatedAt" label="生成时间" min-width="170" />
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" :icon="Download" @click="onDownload(row)">下载</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.report-page { background: var(--iot-bg-page); }
.page-header { display: flex; align-items: center; gap: $spacing-12; margin-bottom: $spacing-16; .page-title { margin: 0; flex: 1; } }
.mb-16 { margin-bottom: $spacing-16; }

.card-title {
  font-size: $font-size-medium; margin: 0 0 $spacing-16;
  color: var(--iot-text-primary); display: flex; align-items: center; gap: $spacing-8;
  &::before { content: ''; display: block; width: 3px; height: 14px; background: var(--iot-color-primary); }
}

.template-list { display: flex; flex-direction: column; gap: $spacing-8; max-height: 480px; overflow-y: auto; }
.template-item {
  padding: $spacing-12; border-radius: $radius-base; cursor: pointer;
  border: 1px solid var(--iot-border-lighter);
  transition: all $transition-fast;
  &:hover { border-color: var(--iot-color-primary-light-5); background: var(--iot-bg-hover); }
  &.active { border-color: var(--iot-color-primary); background: var(--iot-color-primary-light-9); }
}
.tpl-name { font-weight: $font-weight-medium; color: var(--iot-text-primary); display: flex; align-items: center; gap: $spacing-4; margin-bottom: $spacing-4; }
.tpl-desc { margin-top: $spacing-4; }
</style>