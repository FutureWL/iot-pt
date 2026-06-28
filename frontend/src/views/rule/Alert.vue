<script setup lang="ts">
/**
 * 告警记录 — <CrudList> 重构版 (435 → ~210 行)
 *
 * 设计要点:
 *   - 列表/筛选/分页 由 <CrudList> 接管
 *   - 顶部 4 个统计卡用 <KpiCard> 统一
 *   - 级别 INFO/WARN/ERROR/CRITICAL 在 StatusTag 默认 typeMap 已覆盖
 *   - 状态 0/1/2 用 typeMap prop 覆盖(0=未处理=danger)
 *   - 详情对话框保留
 */
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { View, Check, Close } from '@element-plus/icons-vue'
import {
  alertListCrud,
  handleAlert,
  alertStats,
  type IotAlertVO,
  type IotAlertQuery
} from '@/api/rule/alert'
import { CrudList, KpiCard, StatusTag, type ColumnDef, type FilterItem, type StatusType } from '@/ui'

// ========== 列表 ==========
const filters: FilterItem[] = [
  {
    prop: 'level',
    label: '级别',
    type: 'select',
    options: [
      { label: '信息', value: 'INFO' },
      { label: '警告', value: 'WARN' },
      { label: '故障', value: 'ERROR' },
      { label: '紧急', value: 'CRITICAL' }
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

const columns: ColumnDef<IotAlertVO>[] = [
  { prop: 'id', label: 'ID', width: 80 },
  { prop: 'level', label: '级别', width: 100, slot: 'level' },
  { prop: 'title', label: '标题', minWidth: 220, showOverflowTooltip: true },
  { prop: 'deviceKey', label: '设备', minWidth: 120, slot: 'device' },
  { prop: 'ruleName', label: '触发规则', minWidth: 160 },
  { prop: 'status', label: '状态', width: 100, slot: 'status' },
  { prop: 'handler', label: '处理人', width: 100 },
  { prop: 'createdAt', label: '触发时间', width: 170 },
  { label: '操作', width: 240, fixed: 'right', slot: 'actions' }
]

const STATUS_TYPE_MAP: Record<string, StatusType> = { '0': 'danger', '1': 'success', '2': 'info' }
const STATUS_LABEL_MAP: Record<number, string> = { 0: '未处理', 1: '已处理', 2: '已忽略' }

const crudListRef = ref<{ refresh: () => Promise<void> } | null>(null)
function refresh(): void { void crudListRef.value?.refresh() }

// ========== KPI 统计卡 ==========
const stats = ref<Record<string, number>>({})
async function loadStats() {
  try {
    const res: any = await alertStats()
    stats.value = res.data ?? {}
  } catch { /* ignore */ }
}
function statToday(): number {
  return (stats.value['level_WARN'] ?? 0)
       + (stats.value['level_ERROR'] ?? 0)
       + (stats.value['level_CRITICAL'] ?? 0)
}

// ========== 行内操作 ==========
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
    refresh()
  } catch { /* ignore */ }
}

// ========== 详情 ==========
const detailVisible = ref(false)
const detail = ref<IotAlertVO | null>(null)
function showDetail(row: IotAlertVO) { detail.value = row; detailVisible.value = true }

onMounted(loadStats)
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">
      告警记录
    </h2>

    <el-row :gutter="12" class="mb-12">
      <el-col :xs="12" :sm="6">
        <KpiCard
          title="待处理"
          :value="stats['status_0'] ?? 0"
          suffix="条"
          color="danger"
          icon="WarningFilled"
        />
      </el-col>
      <el-col :xs="12" :sm="6">
        <KpiCard
          title="已处理"
          :value="stats['status_1'] ?? 0"
          suffix="条"
          color="success"
          icon="CircleCheck"
        />
      </el-col>
      <el-col :xs="12" :sm="6">
        <KpiCard
          title="今日告警"
          :value="statToday()"
          suffix="条"
          color="warning"
          icon="BellFilled"
        />
      </el-col>
      <el-col :xs="12" :sm="6">
        <KpiCard
          title="紧急"
          :value="stats['level_CRITICAL'] ?? 0"
          suffix="条"
          color="danger"
          icon="Warning"
        />
      </el-col>
    </el-row>

    <CrudList
      ref="crudListRef"
      :api="alertListCrud"
      :columns="columns"
      :filters="filters"
      :row-key="'id'"
      empty-text="暂无告警"
      keyword-placeholder="标题 / 内容"
    >
      <template #column-level="{ row }">
        <StatusTag :value="(row as IotAlertVO).level" />
      </template>

      <template #column-device="{ row }">
        <el-tag size="small" type="info">
          {{ (row as IotAlertVO).deviceKey }}
        </el-tag>
      </template>

      <template #column-status="{ row }">
        <StatusTag
          :value="(row as IotAlertVO).status"
          :label="STATUS_LABEL_MAP[(row as IotAlertVO).status]"
          :type-map="STATUS_TYPE_MAP"
        />
      </template>

      <template #column-actions="{ row }">
        <el-button
          link
          type="primary"
          :icon="View"
          @click="showDetail(row as IotAlertVO)"
        >
          详情
        </el-button>
        <template v-if="(row as IotAlertVO).status === 0">
          <el-button
            link
            type="success"
            :icon="Check"
            @click="onHandle(row as IotAlertVO, 1)"
          >
            处理
          </el-button>
          <el-button
            link
            type="info"
            :icon="Close"
            @click="onHandle(row as IotAlertVO, 2)"
          >
            忽略
          </el-button>
        </template>
      </template>
    </CrudList>

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
          <StatusTag :value="detail.level" />
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <StatusTag
            :value="detail.status"
            :label="STATUS_LABEL_MAP[detail.status]"
            :type-map="STATUS_TYPE_MAP"
          />
        </el-descriptions-item>
        <el-descriptions-item label="设备" :span="2">
          {{ detail.deviceKey }} ({{ detail.productKey }})
        </el-descriptions-item>
        <el-descriptions-item label="规则" :span="2">
          {{ detail.ruleName }}
        </el-descriptions-item>
        <el-descriptions-item label="标题" :span="2">
          {{ detail.title }}
        </el-descriptions-item>
        <el-descriptions-item label="内容" :span="2">
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
        <el-descriptions-item label="处理说明" :span="2">
          {{ detail.handleRemark || '—' }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.mb-12 { margin-bottom: $spacing-12; }
</style>