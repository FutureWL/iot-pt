<script setup lang="ts">
/**
 * 工单管理 — <CrudList> 重构版 (396 → ~120 行)
 *
 * 设计要点:
 *   - 筛选/分页/loading/响应字段映射 全部由 CrudList + useTable 接管
 *   - 顶部 4 个 KPI 卡片用 <KpiCard> 统一(原页面自造 .stat-card)
 *   - 优先级/状态用 <StatusTag> 统一(原页面各自写 statusMap)
 *   - 行内"详情"操作通过 #column-actions 插槽
 */
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, View, Tickets } from '@element-plus/icons-vue'
import {
  workorderCrud,
  getWorkOrderStats,
  type WorkOrderVO,
  type WorkOrderStatus,
  type WorkOrderPriority,
  type WorkOrderStatsVO
} from '@/api/workorder'
import { CrudList, KpiCard, StatusTag, type ColumnDef, type FilterItem } from '@/ui'

const router = useRouter()

const filters: FilterItem[] = [
  {
    prop: 'status',
    label: '状态',
    type: 'select',
    options: [
      { label: '待派单', value: 'PENDING' },
      { label: '处理中', value: 'PROCESSING' },
      { label: '已完成', value: 'COMPLETED' },
      { label: '已超时', value: 'OVERDUE' },
      { label: '已关闭', value: 'CLOSED' }
    ]
  },
  {
    prop: 'priority',
    label: '优先级',
    type: 'select',
    options: [
      { label: '低', value: 'LOW' },
      { label: '普通', value: 'NORMAL' },
      { label: '高', value: 'HIGH' },
      { label: '紧急', value: 'URGENT' }
    ]
  },
  { prop: 'assignee', label: '处理人', type: 'input', placeholder: '用户名' }
]

const columns: ColumnDef<WorkOrderVO>[] = [
  {
    prop: 'workOrderNo',
    label: '工单号',
    width: 180,
    slot: 'workOrderNo'
  },
  { prop: 'title', label: '标题', minWidth: 220, showOverflowTooltip: true },
  { prop: 'deviceKey', label: '设备', minWidth: 140, slot: 'device' },
  { prop: 'priority', label: '优先级', width: 90, slot: 'priority' },
  { prop: 'status', label: '状态', width: 100, slot: 'status' },
  { prop: 'assignee', label: '处理人', width: 100 },
  { prop: 'slaDeadline', label: 'SLA 截止', width: 170, slot: 'sla' },
  { prop: 'createdAt', label: '创建时间', width: 170 },
  { label: '操作', width: 100, fixed: 'right', slot: 'actions' }
]

const statusLabelMap: Record<WorkOrderStatus, string> = {
  PENDING: '待派单',
  PROCESSING: '处理中',
  COMPLETED: '已完成',
  OVERDUE: '已超时',
  CLOSED: '已关闭'
}

function goDetail(row: WorkOrderVO): void {
  router.push(`/workorder/detail/${row.id}`)
}

function isOverdue(deadline?: string): boolean {
  if (!deadline) return false
  return new Date(deadline).getTime() < Date.now()
}

// KPI 卡片 — 与列表独立加载
const stats = ref<WorkOrderStatsVO>({ pending: 0, processing: 0, completed: 0, overdue: 0 })
async function loadStats() {
  try {
    const res: any = await getWorkOrderStats()
    if (res?.data) stats.value = res.data
  } catch { /* ignore */ }
}
onMounted(loadStats)
</script>

<template>
  <div class="page-container workorder-page">
    <div class="page-header">
      <h2 class="page-title">
        工单管理
      </h2>
      <el-button
        type="primary"
        :icon="Plus"
        @click="ElMessage.info('新建工单:待实现(通常从告警自动生成)')"
      >
        新建工单
      </el-button>
    </div>

    <el-row
      :gutter="16"
      class="mb-16"
    >
      <el-col :xs="12" :sm="6">
        <KpiCard
          title="待派单"
          :value="stats.pending"
          suffix="单"
          color="info"
          icon="Tickets"
        />
      </el-col>
      <el-col :xs="12" :sm="6">
        <KpiCard
          title="处理中"
          :value="stats.processing"
          suffix="单"
          color="primary"
          icon="Loading"
        />
      </el-col>
      <el-col :xs="12" :sm="6">
        <KpiCard
          title="已完成"
          :value="stats.completed"
          suffix="单"
          color="success"
          icon="CircleCheck"
        />
      </el-col>
      <el-col :xs="12" :sm="6">
        <KpiCard
          title="已超时 SLA"
          :value="stats.overdue"
          suffix="单"
          color="danger"
          icon="Warning"
        />
      </el-col>
    </el-row>

    <CrudList
      :api="workorderCrud"
      :columns="columns"
      :filters="filters"
      :row-key="'id'"
      empty-text="暂无工单"
      keyword-placeholder="工单号 / 标题"
    >
      <template #column-workOrderNo="{ row }">
        <el-link
          type="primary"
          :underline="false"
          @click="goDetail(row as WorkOrderVO)"
        >
          {{ (row as WorkOrderVO).workOrderNo }}
        </el-link>
      </template>

      <template #column-device="{ row }">
        <el-tag
          size="small"
          type="info"
        >
          {{ (row as WorkOrderVO).deviceKey }}
        </el-tag>
      </template>

      <template #column-priority="{ row }">
        <StatusTag :value="(row as WorkOrderVO).priority as WorkOrderPriority" />
      </template>

      <template #column-status="{ row }">
        <StatusTag
          :value="(row as WorkOrderVO).status"
          :label="statusLabelMap[(row as WorkOrderVO).status as WorkOrderStatus]"
        />
      </template>

      <template #column-sla="{ row }">
        <span :class="{ 'text-danger': isOverdue((row as WorkOrderVO).slaDeadline) }">
          {{ (row as WorkOrderVO).slaDeadline || '—' }}
        </span>
      </template>

      <template #column-actions="{ row }">
        <el-button
          link
          type="primary"
          :icon="View"
          @click="goDetail(row as WorkOrderVO)"
        >
          详情
        </el-button>
      </template>
    </CrudList>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.workorder-page { background: var(--iot-bg-page); }
.page-header { display: flex; align-items: center; gap: $spacing-12; margin-bottom: $spacing-16; .page-title { margin: 0; flex: 1; } }
.mb-16 { margin-bottom: $spacing-16; }
.text-danger { color: var(--iot-color-danger); }
</style>