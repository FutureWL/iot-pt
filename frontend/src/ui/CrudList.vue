<script setup lang="ts" generic="T = Record<string, unknown>, Q extends PageQuery = PageQuery">
/**
 * CrudList — CRUD 列表综合组件
 *
 * 设计目标:让 CRUD 列表页仅需声明 api + columns + filters,
 *        即可自动获得:筛选条 + 表格 + 分页 + loading 状态
 *
 * 使用示例:
 *   <CrudList :api="workorderCrud" :columns="columns" :filters="filters">
 *     <template #toolbar>
 *       <el-button type="primary" @click="openCreate">新建</el-button>
 *     </template>
 *     <template #column-actions="{ row }">
 *       <el-button link @click="onEdit(row)">编辑</el-button>
 *     </template>
 *   </CrudList>
 *
 * 业务页代码量:~80 行 → ~10 行
 */
import { computed } from 'vue'
import type { PageQuery } from '@/types/common'
import type { CrudApi } from '@/api/crud'
import { useTable } from '@/composables/useTable'
import type { FilterItem } from './QueryBar.vue'
import type { ColumnDef } from './DataTable.vue'
import QueryBar from './QueryBar.vue'
import DataTable from './DataTable.vue'
import Pager from './Pager.vue'

interface Props {
  /** CRUD 接口(走 CrudApi 契约) */
  api: CrudApi<T, Q>
  /** 列定义 */
  columns: ColumnDef<T>[]
  /** 筛选字段 */
  filters?: FilterItem[]
  /** 行 key 字段名 */
  rowKey?: string
  /** 初始 query */
  initialQuery?: Partial<Q>
  /** 默认 pageSize */
  pageSize?: number
  /** 空数据文案 */
  emptyText?: string
  /** keyword 占位符 */
  keywordPlaceholder?: string
  /** 是否显示 keyword 输入框 */
  searchable?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  filters: () => [],
  rowKey: 'id',
  initialQuery: () => ({}),
  pageSize: 10,
  emptyText: '暂无数据',
  keywordPlaceholder: '关键字搜索',
  searchable: true
})

// useTable 封装分页 / loading / records
const { loading, records, total, query, fetchPage, reset, setPage, setPageSize } = useTable<T, Q>(
  props.api,
  props.initialQuery,
  { pageSize: props.pageSize }
)

// QueryBar search 事件 → 更新 query + 重置 pageNum = 1
function onSearch(searchValues: Record<string, unknown>): void {
  Object.assign(query, searchValues)
  query.pageNum = 1
  void fetchPage()
}

// Pager 双向绑定
const currentPage = computed<number>({
  get: () => query.pageNum,
  set: (v: number) => setPage(v)
})

const pageSize = computed<number>({
  get: () => query.pageSize,
  set: (v: number) => setPageSize(v)
})

defineExpose({ refresh: fetchPage, reset })
</script>

<template>
  <div class="crud-list">
    <QueryBar
      :filters="props.filters"
      :searchable="props.searchable"
      :keyword-placeholder="props.keywordPlaceholder"
      @search="onSearch"
      @reset="reset"
    >
      <template #extra>
        <slot name="toolbar" />
      </template>
    </QueryBar>

    <DataTable
      :columns="(props.columns as ColumnDef<Record<string, unknown>>[])"
      :data="(records as Record<string, unknown>[])"
      :loading="loading"
      :row-key="props.rowKey"
      :empty-text="props.emptyText"
    >
      <template v-for="col in props.columns.filter((c) => c.slot)" :key="col.slot" #[`column-${col.slot}`]="slotProps">
        <slot :name="`column-${col.slot}`" v-bind="slotProps" />
      </template>
    </DataTable>

    <div class="crud-list__pager">
      <Pager
        v-model:current="currentPage"
        v-model:size="pageSize"
        :total="total"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
.crud-list {
  display: flex;
  flex-direction: column;
  gap: 12px;

  &__pager {
    display: flex;
    justify-content: flex-end;
    margin-top: 4px;
  }
}
</style>
