<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, Refresh, View, Plus, Tickets } from '@element-plus/icons-vue'
import {
  pageWorkOrders,
  getWorkOrderStats,
  type WorkOrderVO,
  type WorkOrderQuery,
  type WorkOrderStatus,
  type WorkOrderPriority
} from '@/api/workorder'

const router = useRouter()
const query = reactive<WorkOrderQuery>({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  status: undefined,
  priority: undefined,
  assignee: undefined
})
const loading = ref(false)
const list = ref<WorkOrderVO[]>([])
const total = ref(0)
const stats = ref({ pending: 0, processing: 0, completed: 0, overdue: 0 })

const statusMap: Record<WorkOrderStatus, { label: string; type: string; color: string }> = {
  PENDING: { label: '待派单', type: 'info', color: '#909399' },
  PROCESSING: { label: '处理中', type: 'primary', color: '#409eff' },
  COMPLETED: { label: '已完成', type: 'success', color: '#67c23a' },
  OVERDUE: { label: '已超时', type: 'danger', color: '#f56c6c' },
  CLOSED: { label: '已关闭', type: 'info', color: '#c0c4cc' }
}
const priorityMap: Record<WorkOrderPriority, { label: string; type: string }> = {
  LOW: { label: '低', type: 'info' },
  NORMAL: { label: '普通', type: '' },
  HIGH: { label: '高', type: 'warning' },
  URGENT: { label: '紧急', type: 'danger' }
}

async function load() {
  loading.value = true
  try {
    const [lRes, sRes]: any[] = await Promise.all([
      pageWorkOrders(query),
      getWorkOrderStats()
    ])
    list.value = lRes.data?.records ?? []
    total.value = lRes.data?.total ?? 0
    stats.value = sRes.data ?? stats.value
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.pageNum = 1
  load()
}
function onReset() {
  query.keyword = ''
  query.status = undefined
  query.priority = undefined
  query.assignee = undefined
  query.pageNum = 1
  load()
}
function onPageChange(p: number) {
  query.pageNum = p
  load()
}
function onSizeChange(s: number) {
  query.pageSize = s
  query.pageNum = 1
  load()
}

function goDetail(row: WorkOrderVO) {
  router.push(`/workorder/detail/${row.id}`)
}

function isOverdue(deadline?: string): boolean {
  if (!deadline) return false
  return new Date(deadline).getTime() < Date.now()
}

onMounted(load)
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
      <el-col
        :xs="12"
        :sm="6"
      >
        <div class="stat-card">
          <el-icon :size="22">
            <Tickets />
          </el-icon>
          <div class="stat-num">
            {{ stats.pending }}
          </div>
          <div class="stat-label">
            待派单
          </div>
        </div>
      </el-col>
      <el-col
        :xs="12"
        :sm="6"
      >
        <div class="stat-card stat-blue">
          <div class="stat-num">
            {{ stats.processing }}
          </div>
          <div class="stat-label">
            处理中
          </div>
        </div>
      </el-col>
      <el-col
        :xs="12"
        :sm="6"
      >
        <div class="stat-card stat-green">
          <div class="stat-num">
            {{ stats.completed }}
          </div>
          <div class="stat-label">
            已完成
          </div>
        </div>
      </el-col>
      <el-col
        :xs="12"
        :sm="6"
      >
        <div class="stat-card stat-red">
          <div class="stat-num">
            {{ stats.overdue }}
          </div>
          <div class="stat-label">
            已超时 SLA
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
            placeholder="工单号 / 标题"
            clearable
            style="width: 220px"
            :prefix-icon="Search"
            @keyup.enter="onSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="query.status"
            placeholder="全部"
            clearable
            style="width: 130px"
          >
            <el-option
              v-for="(v, k) in statusMap"
              :key="k"
              :label="v.label"
              :value="k"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select
            v-model="query.priority"
            placeholder="全部"
            clearable
            style="width: 120px"
          >
            <el-option
              v-for="(v, k) in priorityMap"
              :key="k"
              :label="v.label"
              :value="k"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="处理人">
          <el-input
            v-model="query.assignee"
            placeholder="用户名"
            clearable
            style="width: 140px"
            @keyup.enter="onSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
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
        empty-text="暂无工单"
      >
        <el-table-column
          prop="workOrderNo"
          label="工单号"
          width="180"
        >
          <template #default="{ row }">
            <el-link
              type="primary"
              :underline="false"
              @click="goDetail(row)"
            >
              {{ row.workOrderNo }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column
          prop="title"
          label="标题"
          min-width="220"
          show-overflow-tooltip
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
          </template>
        </el-table-column>
        <el-table-column
          label="优先级"
          width="90"
        >
          <template #default="{ row }">
            <el-tag
              :type="priorityMap[row.priority as WorkOrderPriority]?.type as any"
              size="small"
            >
              {{ priorityMap[row.priority as WorkOrderPriority]?.label || row.priority }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="状态"
          width="100"
        >
          <template #default="{ row }">
            <el-tag
              :type="statusMap[row.status as WorkOrderStatus]?.type as any"
              size="small"
            >
              {{ statusMap[row.status as WorkOrderStatus]?.label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="assignee"
          label="处理人"
          width="100"
        />
        <el-table-column
          label="SLA 截止"
          width="170"
        >
          <template #default="{ row }">
            <span :class="{ 'text-danger': isOverdue(row.slaDeadline) }">
              {{ row.slaDeadline || '—' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column
          prop="createdAt"
          label="创建时间"
          width="170"
        />
        <el-table-column
          label="操作"
          width="100"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :icon="View"
              @click="goDetail(row)"
            >
              详情
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
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.workorder-page { background: var(--iot-bg-page); }
.page-header { display: flex; align-items: center; gap: $spacing-12; margin-bottom: $spacing-16; .page-title { margin: 0; flex: 1; } }
.mb-16 { margin-bottom: $spacing-16; }

.stat-card {
  background: var(--iot-bg-card);
  border-radius: $radius-large;
  padding: $spacing-16;
  text-align: center;
  box-shadow: var(--iot-shadow-light);
  margin-bottom: $spacing-12;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-4;
}

.stat-num {
  font-size: $font-size-huge;
  font-weight: $font-weight-semibold;
  color: var(--iot-text-primary);
  font-family: var(--iot-font-family-code);
}

.stat-label {
  font-size: $font-size-extra-small;
  color: var(--iot-text-secondary);
}

.stat-blue .stat-num { color: var(--iot-color-primary); }
.stat-green .stat-num { color: var(--iot-color-success); }
.stat-red .stat-num { color: var(--iot-color-danger); }

.search-bar { margin-bottom: $spacing-12; padding: $spacing-16; :deep(.el-form-item) { margin-bottom: 0; } }
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: $spacing-16; }
</style>