<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, View, Check, Close } from '@element-plus/icons-vue'
import {
  pageAlerts,
  handleAlert,
  alertStats,
  type IotAlertVO,
  type IotAlertQuery
} from '@/api/rule/alert'

const query = reactive<IotAlertQuery>({
  pageNum: 1, pageSize: 10, keyword: '',
  level: '', status: undefined
})
const loading = ref(false)
const list = ref<IotAlertVO[]>([])
const total = ref(0)
const stats = ref<Record<string, number>>({})

const levelMap: Record<string, { label: string; color: string }> = {
  INFO: { label: '信息', color: '#909399' },
  WARN: { label: '警告', color: '#E6A23C' },
  ERROR: { label: '故障', color: '#F56C6C' },
  CRITICAL: { label: '紧急', color: '#F56C6C' }
}
const statusMap: Record<number, { label: string; type: string }> = {
  0: { label: '未处理', type: 'danger' },
  1: { label: '已处理', type: 'success' },
  2: { label: '已忽略', type: 'info' }
}

async function load() {
  loading.value = true
  try {
    const [listRes, statsRes]: any[] = await Promise.all([pageAlerts(query), alertStats()])
    list.value = listRes.data.records ?? []
    total.value = listRes.data.total ?? 0
    stats.value = statsRes.data ?? {}
  } finally {
    loading.value = false
  }
}

function onSearch() { query.pageNum = 1; load() }
function onReset() {
  query.keyword = ''; query.level = ''; query.status = undefined; query.pageNum = 1; load()
}
function onPageChange(p: number) { query.pageNum = p; load() }
function onSizeChange(s: number) { query.pageSize = s; query.pageNum = 1; load() }

async function onHandle(row: IotAlertVO, status: 1 | 2) {
  const action = status === 1 ? '处理' : '忽略'
  let remark = ''
  if (status === 1) {
    const promptRes = await ElMessageBox.prompt(
      `确认标记为「已处理」?可填写处理说明:`, `处理告警: ${row.title}`,
      { inputPlaceholder: '处理说明(可空)', confirmButtonText: '确定', cancelButtonText: '取消' }
    ).catch(() => null)
    if (promptRes === null) return
    remark = promptRes.value || ''
  } else {
    await ElMessageBox.confirm(`确认忽略告警「${row.title}」?`, '忽略确认', { type: 'warning' })
  }
  try {
    await handleAlert(row.id, status, remark)
    ElMessage.success(`${action}成功`)
    load()
  } catch {}
}

const detailVisible = ref(false)
const detail = ref<IotAlertVO | null>(null)
function showDetail(row: IotAlertVO) {
  detail.value = row
  detailVisible.value = true
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">
      告警记录
    </h2>

    <!-- 统计卡片 -->
    <el-row
      :gutter="12"
      class="mb-12"
    >
      <el-col
        :xs="12"
        :sm="6"
      >
        <div class="stat-card stat-red">
          <div class="stat-num">
            {{ stats['status_0'] ?? 0 }}
          </div>
          <div class="stat-label">
            待处理
          </div>
        </div>
      </el-col>
      <el-col
        :xs="12"
        :sm="6"
      >
        <div class="stat-card stat-green">
          <div class="stat-num">
            {{ stats['status_1'] ?? 0 }}
          </div>
          <div class="stat-label">
            已处理
          </div>
        </div>
      </el-col>
      <el-col
        :xs="12"
        :sm="6"
      >
        <div class="stat-card stat-cyan">
          <div class="stat-num">
            {{ (stats['level_WARN'] ?? 0) + (stats['level_ERROR'] ?? 0) + (stats['level_CRITICAL'] ?? 0) }}
          </div>
          <div class="stat-label">
            今日告警
          </div>
        </div>
      </el-col>
      <el-col
        :xs="12"
        :sm="6"
      >
        <div class="stat-card stat-orange">
          <div class="stat-num">
            {{ stats['level_CRITICAL'] ?? 0 }}
          </div>
          <div class="stat-label">
            紧急
          </div>
        </div>
      </el-col>
    </el-row>

    <div class="page-card search-bar">
      <el-form
        :inline="true"
        @submit.prevent
      >
        <el-form-item label="关键字">
          <el-input
            v-model="query.keyword"
            placeholder="标题 / 内容"
            clearable
            style="width: 220px"
            @keyup.enter="onSearch"
          />
        </el-form-item>
        <el-form-item label="级别">
          <el-select
            v-model="query.level"
            placeholder="全部"
            clearable
            style="width: 120px"
          >
            <el-option
              label="信息"
              value="INFO"
            />
            <el-option
              label="警告"
              value="WARN"
            />
            <el-option
              label="故障"
              value="ERROR"
            />
            <el-option
              label="紧急"
              value="CRITICAL"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="query.status"
            placeholder="全部"
            clearable
            style="width: 120px"
          >
            <el-option
              label="未处理"
              :value="0"
            />
            <el-option
              label="已处理"
              :value="1"
            />
            <el-option
              label="已忽略"
              :value="2"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :icon="Search"
            @click="onSearch"
          >
            查询
          </el-button>
          <el-button
            :icon="Refresh"
            @click="onReset"
          >
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="page-card">
      <el-table
        v-loading="loading"
        :data="list"
        stripe
        border
      >
        <el-table-column
          prop="id"
          label="ID"
          width="80"
        />
        <el-table-column
          label="级别"
          width="100"
        >
          <template #default="{ row }">
            <el-tag :style="{ background: levelMap[row.level]?.color + '20', color: levelMap[row.level]?.color, border: 'none' }">
              {{ levelMap[row.level]?.label || row.level }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="title"
          label="标题"
          min-width="220"
          show-overflow-tooltip
        />
        <el-table-column
          prop="deviceKey"
          label="设备"
          width="120"
        >
          <template #default="{ row }">
            <el-tag
              size="small"
              type="info"
            >
              {{ row.deviceKey }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="ruleName"
          label="触发规则"
          min-width="160"
        />
        <el-table-column
          label="状态"
          width="100"
        >
          <template #default="{ row }">
            <el-tag
              :type="statusMap[row.status]?.type as any"
              size="small"
            >
              {{ statusMap[row.status]?.label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="handler"
          label="处理人"
          width="100"
        />
        <el-table-column
          prop="createdAt"
          label="触发时间"
          width="170"
        />
        <el-table-column
          label="操作"
          width="240"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :icon="View"
              @click="showDetail(row)"
            >
              详情
            </el-button>
            <el-button
              v-if="row.status === 0"
              link
              type="success"
              :icon="Check"
              @click="onHandle(row, 1)"
            >
              处理
            </el-button>
            <el-button
              v-if="row.status === 0"
              link
              type="info"
              :icon="Close"
              @click="onHandle(row, 2)"
            >
              忽略
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无告警" />
        </template>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="onPageChange"
          @size-change="onSizeChange"
        />
      </div>
    </div>

    <!-- 详情对话框 -->
    <el-dialog
      v-model="detailVisible"
      title="告警详情"
      width="640px"
    >
      <el-descriptions
        v-if="detail"
        :column="2"
        border
      >
        <el-descriptions-item label="级别">
          <el-tag :style="{ background: levelMap[detail.level]?.color + '20', color: levelMap[detail.level]?.color, border: 'none' }">
            {{ levelMap[detail.level]?.label || detail.level }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusMap[detail.status]?.type as any">
            {{ statusMap[detail.status]?.label }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item
          label="设备"
          :span="2"
        >
          {{ detail.deviceKey }} ({{ detail.productKey }})
        </el-descriptions-item>
        <el-descriptions-item
          label="规则"
          :span="2"
        >
          {{ detail.ruleName }}
        </el-descriptions-item>
        <el-descriptions-item
          label="标题"
          :span="2"
        >
          {{ detail.title }}
        </el-descriptions-item>
        <el-descriptions-item
          label="内容"
          :span="2"
        >
          {{ detail.content }}
        </el-descriptions-item>
        <el-descriptions-item label="触发时间">
          {{ detail.createdAt }}
        </el-descriptions-item>
        <el-descriptions-item label="处理时间">
          {{ detail.handleTime || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="处理人">
          {{ detail.handler || '—' }}
        </el-descriptions-item>
        <el-descriptions-item
          label="处理说明"
          :span="2"
        >
          {{ detail.handleRemark || '—' }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.mb-12 { margin-bottom: 12px; }
.search-bar { margin-bottom: 12px; padding: 16px; :deep(.el-form-item) { margin-bottom: 0; } }
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 16px; }

.stat-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px 12px;
  text-align: center;
  box-shadow: 0 1px 4px rgba(0,0,0,0.04);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  margin-bottom: 12px;
}
.stat-num { font-size: 24px; font-weight: 600; color: #303133; }
.stat-label { font-size: 12px; color: #909399; }
.stat-red { background: linear-gradient(135deg, #fef0f0 0%, #fff 100%); color: #f56c6c; }
.stat-green { background: linear-gradient(135deg, #f0f9eb 0%, #fff 100%); color: #67c23a; }
.stat-orange { background: linear-gradient(135deg, #fdf6ec 0%, #fff 100%); color: #e6a23c; }
.stat-cyan { background: linear-gradient(135deg, #ecfeff 0%, #fff 100%); color: #06b6d4; }
</style>