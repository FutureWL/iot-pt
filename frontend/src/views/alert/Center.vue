<script setup lang="ts">
/**
 * 告警中心 — <CrudList> 重构版 (483 → ~200 行)
 *
 * 设计要点:
 *   - 列表/筛选/分页 由 <CrudList> 接管
 *   - 顶部 4 张级别卡保留(自带 pulse 动画等样式)
 *   - 告警级别是 NOTICE/ABNORMAL/SERIOUS/URGENT,StatusTag 默认 typeMap
 *     不覆盖,自定义 4 个
 *   - 状态 0/1/2 语义与 StatusTag 默认不同(0=未处理=danger),
 *     自定义 typeMap
 *   - 详情对话框保留(用 el-descriptions 替代 DescriptionList
 *     是因为它支持 :span 控制)
 */
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { View, Check, Close, Refresh, Setting } from '@element-plus/icons-vue'
import {
  alertCrud,
  getAlertLevelStats,
  handleAlertCenter,
  createWorkOrderFromAlert,
  type AlertCenterVO,
  type AlertCenterQuery,
  type AlertLevelStat,
  type AlertLevel
} from '@/api/alert/center'
import { CrudList, StatusTag, type ColumnDef, type FilterItem, type StatusType } from '@/ui'

const router = useRouter()

// ========== 列表 ==========
const filters: FilterItem[] = [
  {
    prop: 'level',
    label: '级别',
    type: 'select',
    options: [
      { label: '注意', value: 'NOTICE' },
      { label: '异常', value: 'ABNORMAL' },
      { label: '严重', value: 'SERIOUS' },
      { label: '紧急', value: 'URGENT' }
    ]
  },
  {
    prop: 'status',
    label: '状态',
    type: 'select',
    options: [
      { label: '未处理', value: 0 },
      { label: '已处理', value: 1 },
      { label: '已忽略', value: 2 }
    ]
  }
]

const columns: ColumnDef<AlertCenterVO>[] = [
  { prop: 'level', label: '级别', width: 100, slot: 'level' },
  { prop: 'title', label: '标题', minWidth: 220, showOverflowTooltip: true },
  { prop: 'alertType', label: '告警类型', width: 120 },
  { prop: 'deviceKey', label: '设备', minWidth: 180, slot: 'device' },
  { prop: 'status', label: '状态', width: 100, slot: 'status' },
  { prop: 'workOrderId', label: '工单', width: 100, slot: 'workOrder' },
  { prop: 'handler', label: '处理人', width: 100 },
  { prop: 'alertTime', label: '触发时间', width: 170 },
  { label: '操作', width: 280, fixed: 'right', slot: 'actions' }
]

// StatusTag 自定义映射(覆盖默认值)
const STATUS_TYPE_MAP: Record<string, StatusType> = {
  '0': 'danger',     // 未处理
  '1': 'success',    // 已处理
  '2': 'info'        // 已忽略
}
const STATUS_LABEL_MAP: Record<number, string> = { 0: '未处理', 1: '已处理', 2: '已忽略' }

const LEVEL_TYPE_MAP: Record<AlertLevel, StatusType> = {
  NOTICE: 'info',
  ABNORMAL: 'warning',
  SERIOUS: 'danger',
  URGENT: 'danger'
}
const LEVEL_LABEL_MAP: Record<AlertLevel, string> = {
  NOTICE: '注意', ABNORMAL: '异常', SERIOUS: '严重', URGENT: '紧急'
}

// 列表 refresh 句柄
const crudListRef = ref<{ refresh: () => Promise<void> } | null>(null)
function refresh(): void { void crudListRef.value?.refresh() }

// ========== KPI 统计卡 ==========
const stats = ref<AlertLevelStat[]>([])
async function loadStats() {
  try {
    const res: any = await getAlertLevelStats()
    stats.value = res.data ?? []
  } catch { /* ignore */ }
}
function statCount(level: AlertLevel): number {
  return stats.value.find(s => s.level === level)?.count ?? 0
}

// ========== 行内操作 ==========
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
    refresh()
  } catch { /* ignore */ }
}

async function onCreateWorkOrder(row: AlertCenterVO) {
  try {
    await ElMessageBox.confirm(`确认根据该告警生成工单?`, '生成工单', { type: 'info' })
    const res: any = await createWorkOrderFromAlert(row.id)
    const workOrderId = res.data?.workOrderId
    ElMessage.success('工单已生成')
    if (workOrderId) router.push(`/workorder/detail/${workOrderId}`)
  } catch { /* ignore */ }
}

// ========== 详情对话框 ==========
const detailVisible = ref(false)
const detail = ref<AlertCenterVO | null>(null)
function showDetail(row: AlertCenterVO) { detail.value = row; detailVisible.value = true }

onMounted(loadStats)
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
          @click="refresh"
        >
          刷新
        </el-button>
      </div>
    </div>

    <!-- 4 级告警统计卡(保留自定义样式:pulse 动画 + 渐变背景) -->
    <el-row
      :gutter="16"
      class="mb-16"
    >
      <el-col :xs="12" :sm="6">
        <div class="level-card level-notice">
          <div class="level-num">{{ statCount('NOTICE') }}</div>
          <div class="level-label">注意</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="level-card level-abnormal">
          <div class="level-num">{{ statCount('ABNORMAL') }}</div>
          <div class="level-label">异常</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="level-card level-serious">
          <div class="level-num">{{ statCount('SERIOUS') }}</div>
          <div class="level-label">严重</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="level-card level-urgent">
          <div class="level-num">{{ statCount('URGENT') }}</div>
          <div class="level-label">紧急</div>
        </div>
      </el-col>
    </el-row>

    <CrudList
      ref="crudListRef"
      :api="alertCrud"
      :columns="columns"
      :filters="filters"
      :row-key="'id'"
      empty-text="暂无告警"
      keyword-placeholder="标题 / 内容 / 设备 Key"
    >
      <template #column-level="{ row }">
        <StatusTag
          :value="(row as AlertCenterVO).level"
          :label="LEVEL_LABEL_MAP[(row as AlertCenterVO).level]"
          :type-map="LEVEL_TYPE_MAP"
        />
      </template>

      <template #column-device="{ row }">
        <el-tag size="small" type="info">
          {{ (row as AlertCenterVO).deviceKey }}
        </el-tag>
        <span class="text-secondary text-xs ml-4">{{ (row as AlertCenterVO).deviceName }}</span>
      </template>

      <template #column-status="{ row }">
        <StatusTag
          :value="(row as AlertCenterVO).status"
          :label="STATUS_LABEL_MAP[(row as AlertCenterVO).status]"
          :type-map="STATUS_TYPE_MAP"
        />
      </template>

      <template #column-workOrder="{ row }">
        <el-link
          v-if="(row as AlertCenterVO).workOrderId"
          type="primary"
          :underline="false"
          @click="router.push(`/workorder/detail/${(row as AlertCenterVO).workOrderId}`)"
        >
          #{{ (row as AlertCenterVO).workOrderId }}
        </el-link>
        <span v-else class="text-disabled">—</span>
      </template>

      <template #column-actions="{ row }">
        <el-button
          link
          type="primary"
          :icon="View"
          @click="showDetail(row as AlertCenterVO)"
        >
          详情
        </el-button>
        <template v-if="(row as AlertCenterVO).status === 0">
          <el-button
            link
            type="success"
            :icon="Check"
            @click="onHandle(row as AlertCenterVO, 1)"
          >
            处理
          </el-button>
          <el-button
            link
            type="info"
            :icon="Close"
            @click="onHandle(row as AlertCenterVO, 2)"
          >
            忽略
          </el-button>
          <el-button
            link
            type="warning"
            @click="onCreateWorkOrder(row as AlertCenterVO)"
          >
            生成工单
          </el-button>
        </template>
      </template>
    </CrudList>

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
          <StatusTag
            :value="detail.level"
            :label="LEVEL_LABEL_MAP[detail.level]"
            :type-map="LEVEL_TYPE_MAP"
          />
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <StatusTag
            :value="detail.status"
            :label="STATUS_LABEL_MAP[detail.status]"
            :type-map="STATUS_TYPE_MAP"
          />
        </el-descriptions-item>
        <el-descriptions-item label="告警类型">
          {{ detail.alertType }}
        </el-descriptions-item>
        <el-descriptions-item label="关联设备">
          {{ detail.deviceKey }} ({{ detail.deviceName }})
        </el-descriptions-item>
        <el-descriptions-item label="标题" :span="2">
          {{ detail.title }}
        </el-descriptions-item>
        <el-descriptions-item label="内容" :span="2">
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
.text-secondary { color: var(--iot-text-regular); }
.text-disabled { color: var(--iot-text-disabled); }
.text-xs { font-size: $font-size-extra-small; }

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
.level-num { font-size: $font-size-mega; font-weight: $font-weight-bold; font-family: var(--iot-font-family-code); color: var(--iot-text-primary); }
.level-label { font-size: $font-size-extra-small; color: var(--iot-text-secondary); margin-top: $spacing-4; }

@keyframes pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(245, 108, 108, 0.4); }
  50% { box-shadow: 0 0 0 6px rgba(245, 108, 108, 0); }
}
</style>