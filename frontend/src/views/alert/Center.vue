<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, View, Check, Close, Setting } from '@element-plus/icons-vue'
import {
  pageAlertsCenter,
  getAlertLevelStats,
  handleAlertCenter,
  createWorkOrderFromAlert,
  type AlertCenterVO,
  type AlertCenterQuery,
  type AlertLevelStat,
  type AlertLevel
} from '@/api/alert/center'

const router = useRouter()
const query = reactive<AlertCenterQuery>({
  pageNum: 1, pageSize: 10, keyword: '',
  level: undefined, status: undefined
})
const loading = ref(false)
const list = ref<AlertCenterVO[]>([])
const total = ref(0)
const stats = ref<AlertLevelStat[]>([])

const levelMap: Record<AlertLevel, { label: string; color: string; tag: string }> = {
  NOTICE: { label: '注意', color: '#909399', tag: 'info' },
  ABNORMAL: { label: '异常', color: '#e6a23c', tag: 'warning' },
  SERIOUS: { label: '严重', color: '#f78989', tag: 'danger' },
  URGENT: { label: '紧急', color: '#f56c6c', tag: 'danger' }
}
const statusMap: Record<number, { label: string; type: string }> = {
  0: { label: '未处理', type: 'danger' },
  1: { label: '已处理', type: 'success' },
  2: { label: '已忽略', type: 'info' }
}

async function load() {
  loading.value = true
  try {
    const [lRes, sRes]: any[] = await Promise.all([
      pageAlertsCenter(query),
      getAlertLevelStats()
    ])
    list.value = lRes.data?.records ?? []
    total.value = lRes.data?.total ?? 0
    stats.value = sRes.data ?? []
  } finally {
    loading.value = false
  }
}

function statCount(level: AlertLevel): number {
  return stats.value.find(s => s.level === level)?.count ?? 0
}

function onSearch() { query.pageNum = 1; load() }
function onReset() { query.keyword = ''; query.level = undefined; query.status = undefined; query.pageNum = 1; load() }
function onPageChange(p: number) { query.pageNum = p; load() }
function onSizeChange(s: number) { query.pageSize = s; query.pageNum = 1; load() }

async function onHandle(row: AlertCenterVO, status: 1 | 2) {
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
    await handleAlertCenter(row.id, status, remark)
    ElMessage.success(`${action}成功`)
    load()
  } catch {}
}

async function onCreateWorkOrder(row: AlertCenterVO) {
  try {
    await ElMessageBox.confirm(`确认根据该告警生成工单?`, '生成工单', { type: 'info' })
    const res: any = await createWorkOrderFromAlert(row.id)
    const workOrderId = res.data?.workOrderId
    ElMessage.success('工单已生成')
    if (workOrderId) router.push(`/workorder/detail/${workOrderId}`)
  } catch {}
}

const detailVisible = ref(false)
const detail = ref<AlertCenterVO | null>(null)
function showDetail(row: AlertCenterVO) { detail.value = row; detailVisible.value = true }

onMounted(load)
</script>

<template>
  <div class="page-container alert-page">
    <div class="page-header">
      <h2 class="page-title">
        告警中心
      </h2>
      <div class="header-tools">
        <el-button
          :icon="Setting"
          @click="ElMessage.info('智能过滤规则配置:待实现')"
        >
          过滤规则
        </el-button>
        <el-button
          :icon="Refresh"
          @click="load"
        >
          刷新
        </el-button>
      </div>
    </div>

    <!-- 4 级告警统计卡 -->
    <el-row
      :gutter="16"
      class="mb-16"
    >
      <el-col
        :xs="12"
        :sm="6"
      >
        <div class="level-card level-notice">
          <div class="level-num">
            {{ statCount('NOTICE') }}
          </div>
          <div class="level-label">
            注意
          </div>
        </div>
      </el-col>
      <el-col
        :xs="12"
        :sm="6"
      >
        <div class="level-card level-abnormal">
          <div class="level-num">
            {{ statCount('ABNORMAL') }}
          </div>
          <div class="level-label">
            异常
          </div>
        </div>
      </el-col>
      <el-col
        :xs="12"
        :sm="6"
      >
        <div class="level-card level-serious">
          <div class="level-num">
            {{ statCount('SERIOUS') }}
          </div>
          <div class="level-label">
            严重
          </div>
        </div>
      </el-col>
      <el-col
        :xs="12"
        :sm="6"
      >
        <div class="level-card level-urgent">
          <div class="level-num">
            {{ statCount('URGENT') }}
          </div>
          <div class="level-label">
            紧急
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 搜索栏 -->
    <div class="page-card search-bar">
      <el-form
        :inline="true"
        @submit.prevent
      >
        <el-form-item label="关键字">
          <el-input
            v-model="query.keyword"
            placeholder="标题 / 内容 / 设备 Key"
            clearable
            style="width: 220px"
            :prefix-icon="Search"
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
              v-for="(v, k) in levelMap"
              :key="k"
              :label="v.label"
              :value="k"
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
            @click="onSearch"
          >
            查询
          </el-button>
          <el-button @click="onReset">
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 列表 -->
    <div class="page-card">
      <el-table
        v-loading="loading"
        :data="list"
        stripe
        border
        empty-text="暂无告警"
      >
        <el-table-column
          label="级别"
          width="100"
        >
          <template #default="{ row }">
            <el-tag :style="{ background: levelMap[row.level as AlertLevel]?.color + '20', color: levelMap[row.level as AlertLevel]?.color, border: 'none' }">
              {{ levelMap[row.level as AlertLevel]?.label || row.level }}
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
          prop="alertType"
          label="告警类型"
          width="120"
        />
        <el-table-column
          label="设备"
          min-width="140"
        >
          <template #default="{ row }">
            <el-tag
              size="small"
              type="info"
            >
              {{ row.deviceKey }}
            </el-tag>
            <span class="text-secondary text-xs ml-4">{{ row.deviceName }}</span>
          </template>
        </el-table-column>
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
          label="工单"
          width="100"
        >
          <template #default="{ row }">
            <el-link
              v-if="row.workOrderId"
              type="primary"
              :underline="false"
              @click="router.push(`/workorder/detail/${row.workOrderId}`)"
            >
              #{{ row.workOrderId }}
            </el-link>
            <span
              v-else
              class="text-disabled"
            >—</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="handler"
          label="处理人"
          width="100"
        />
        <el-table-column
          prop="alertTime"
          label="触发时间"
          width="170"
        />
        <el-table-column
          label="操作"
          width="280"
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
            <el-button
              v-if="row.status === 0"
              link
              type="warning"
              @click="onCreateWorkOrder(row)"
            >
              生成工单
            </el-button>
          </template>
        </el-table-column>
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

    <!-- 详情 -->
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
          <el-tag :style="{ background: levelMap[detail.level as AlertLevel]?.color + '20', color: levelMap[detail.level as AlertLevel]?.color, border: 'none' }">
            {{ levelMap[detail.level as AlertLevel]?.label || detail.level }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusMap[detail.status]?.type as any">
            {{ statusMap[detail.status]?.label }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="告警类型">
          {{ detail.alertType }}
        </el-descriptions-item>
        <el-descriptions-item label="关联设备">
          {{ detail.deviceKey }} ({{ detail.deviceName }})
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
          {{ detail.content || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="触发时间">
          {{ detail.alertTime }}
        </el-descriptions-item>
        <el-descriptions-item label="处理时间">
          {{ detail.handleTime || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="处理人">
          {{ detail.handler || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="关联工单">
          <el-link
            v-if="detail.workOrderId"
            type="primary"
            @click="router.push(`/workorder/detail/${detail.workOrderId}`)"
          >
            #{{ detail.workOrderId }}
          </el-link>
          <span v-else>—</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.alert-page { background: var(--iot-bg-page); }
.page-header { display: flex; align-items: center; gap: $spacing-12; margin-bottom: $spacing-16; .page-title { margin: 0; flex: 1; } }
.header-tools { display: flex; gap: $spacing-8; }
.mb-16 { margin-bottom: $spacing-16; }
.ml-4 { margin-left: $spacing-4; }

.level-card {
  background: var(--iot-bg-card);
  border-radius: $radius-large;
  padding: $spacing-20 $spacing-16;
  text-align: center;
  box-shadow: var(--iot-shadow-light);
  margin-bottom: $spacing-12;
  border-left: 4px solid var(--iot-color-info);
  &.level-notice { border-left-color: var(--iot-color-info); }
  &.level-abnormal { border-left-color: var(--iot-color-warning); background: linear-gradient(135deg, var(--iot-color-warning-light) 0%, #fff 100%); }
  &.level-serious { border-left-color: #f78989; background: linear-gradient(135deg, var(--iot-color-danger-light) 0%, #fff 100%); }
  &.level-urgent { border-left-color: var(--iot-color-danger); background: linear-gradient(135deg, var(--iot-color-danger-light) 0%, #fff 100%); animation: pulse 2s infinite; }
}
.level-num { font-size: 32px; font-weight: $font-weight-bold; font-family: var(--iot-font-family-code); color: var(--iot-text-primary); }
.level-label { font-size: $font-size-extra-small; color: var(--iot-text-secondary); margin-top: $spacing-4; }

@keyframes pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(245, 108, 108, 0.4); }
  50% { box-shadow: 0 0 0 6px rgba(245, 108, 108, 0); }
}

.search-bar { margin-bottom: $spacing-12; padding: $spacing-16; :deep(.el-form-item) { margin-bottom: 0; } }
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: $spacing-16; }
</style>