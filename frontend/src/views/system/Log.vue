<script setup lang="ts">
/**
 * 操作日志 — CrudList 重构演示(335 行 → ~140 行)
 */
import { ref, computed } from 'vue'
import { View } from '@element-plus/icons-vue'
import {
  operationLogCrud,
  getOperationLog,
  type SysOperationLogVO
} from '@/api/system/log'
import { CrudList, StatusTag, DescriptionList, EmptyState } from '@/ui'
import type { ColumnDef, FilterItem } from '@/ui'

const filters: FilterItem[] = [
  { prop: 'username', label: '用户名', placeholder: '操作人' },
  {
    prop: 'module',
    label: '模块',
    type: 'select',
    options: ['用户管理', '角色管理', '设备管理', '产品管理', '工单管理', '告警管理', '知识库'].map((m) => ({ label: m, value: m }))
  },
  {
    prop: 'action',
    label: '操作类型',
    type: 'select',
    options: ['登录', '登出', '新增', '修改', '删除', '查询', '导出'].map((a) => ({ label: a, value: a }))
  },
  {
    prop: 'status',
    label: '状态',
    type: 'select',
    options: [
      { label: '成功', value: 1 },
      { label: '失败', value: 0 }
    ]
  }
]

const columns: ColumnDef<SysOperationLogVO>[] = [
  { prop: 'ts', label: '时间', width: 170 },
  { prop: 'username', label: '操作人', width: 100 },
  { prop: 'module', label: '模块', width: 120 },
  { prop: 'action', label: '操作', width: 100 },
  { prop: 'method', label: 'HTTP', width: 80, slot: 'method' },
  { prop: 'url', label: '请求路径', minWidth: 220, showOverflowTooltip: true },
  { prop: 'ip', label: 'IP', width: 140 },
  { label: '耗时', width: 90, slot: 'cost' },
  { prop: 'status', label: '状态', width: 80, slot: 'status' },
  { label: '操作', width: 80, fixed: 'right', slot: 'actions' }
]

const detailVisible = ref(false)
const detail = ref<SysOperationLogVO | null>(null)

async function showDetail(row: SysOperationLogVO): Promise<void> {
  const res: any = await getOperationLog(row.id)
  detail.value = res.data ?? row
  detailVisible.value = true
}

const detailItems = computed(() => {
  if (!detail.value) return []
  const d = detail.value
  return [
    { label: '操作时间', value: d.ts },
    { label: '操作人', value: `${d.username} (#${d.userId})` },
    { label: '模块', value: d.module },
    { label: '操作', value: d.action },
    { label: 'HTTP 方法', value: d.method ?? '—' },
    { label: '状态', value: d.status === 1 ? '成功' : '失败' },
    { label: '请求路径', value: d.url ?? '—', span: 2 },
    { label: 'IP', value: d.ip ?? '—' },
    { label: '耗时', value: `${d.costMs ?? 0} ms` },
    { label: 'UA', value: d.userAgent ?? '—', span: 2 },
    { label: '请求参数', value: d.params ?? '—', span: 2 }
  ]
})
</script>

<template>
  <div class="log-page page-container">
    <div class="page-header">
      <h2 class="page-title">
        操作日志
      </h2>
    </div>

    <CrudList
      :api="operationLogCrud"
      :columns="columns"
      :filters="filters"
      :page-size="20"
      empty-text="暂无日志"
      keyword-placeholder="关键字搜索"
    >
      <template #column-method="{ row }">
        <el-tag
          size="small"
          :type="(row as SysOperationLogVO).method === 'GET' ? 'info' : 'primary'"
        >
          {{ (row as SysOperationLogVO).method }}
        </el-tag>
      </template>

      <template #column-cost="{ row }">
        <span :class="{ 'text-warning': ((row as SysOperationLogVO).costMs ?? 0) > 1000 }">
          {{ (row as SysOperationLogVO).costMs ?? 0 }} ms
        </span>
      </template>

      <template #column-status="{ row }">
        <StatusTag :value="(row as SysOperationLogVO).status" />
      </template>

      <template #column-actions="{ row }">
        <el-button
          link
          type="primary"
          size="small"
          :icon="View"
          @click="showDetail(row as SysOperationLogVO)"
        >
          详情
        </el-button>
      </template>
    </CrudList>

    <el-dialog
      v-model="detailVisible"
      title="日志详情"
      width="720px"
    >
      <DescriptionList
        v-if="detail"
        :items="detailItems"
        :column="2"
      />
      <EmptyState
        v-else
        text="暂无详情"
        image="noData"
      />
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.log-page { background: var(--iot-bg-page); }
.page-header { display: flex; align-items: center; gap: $spacing-12; margin-bottom: $spacing-16; .page-title { margin: 0; flex: 1; } }
</style>
