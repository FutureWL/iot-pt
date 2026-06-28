<script setup lang="ts">
/**
 * DataTable — 数据表格封装
 *
 * 设计目标:统一 6+ 处 el-table 重复
 *
 * 使用示例:
 *   <DataTable :columns="columns" :data="rows" />
 *   // 自定义列:
 *   <DataTable :columns="columns" :data="rows">
 *     <template #column-status="{ row }">
 *       <StatusTag :value="row.status" />
 *     </template>
 *   </DataTable>
 */
export interface ColumnDef<T = Record<string, unknown>> {
  /** 数据字段名 */
  prop: string
  /** 列标题 */
  label: string
  /** 列宽 */
  width?: number | string
  /** 最小列宽 */
  minWidth?: number | string
  /** 自定义列插槽名(父组件用 #column-<slot> 注入) */
  slot?: string
  /** 对齐方式 */
  align?: 'left' | 'center' | 'right'
  /** 固定列 */
  fixed?: 'left' | 'right'
  /** 默认格式化函数(无 slot 时生效) */
  formatter?: (row: T) => unknown
}

interface Props {
  /** 列定义 */
  columns: ColumnDef[]
  /** 表格数据 */
  data: readonly unknown[]
  /** 行 key 字段名,默认 'id' */
  rowKey?: string
  /** 加载态 */
  loading?: boolean
  /** 空数据文案 */
  emptyText?: string
  /** 斑马纹 */
  stripe?: boolean
  /** 边框 */
  border?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  rowKey: 'id',
  loading: false,
  emptyText: '暂无数据',
  stripe: true,
  border: true
})
</script>

<template>
  <el-table
    :data="[...props.data]"
    :row-key="props.rowKey"
    :stripe="props.stripe"
    :border="props.border"
    :empty-text="props.emptyText"
    :loading="props.loading"
  >
    <template #empty>
      <div class="data-table-empty">{{ props.emptyText }}</div>
    </template>
    <el-table-column
      v-for="col in props.columns"
      :key="col.prop"
      :prop="col.prop"
      :label="col.label"
      :width="col.width"
      :min-width="col.minWidth"
      :align="col.align"
      :fixed="col.fixed"
    >
      <template v-if="col.slot" #default="{ row }">
        <slot :name="`column-${col.slot}`" :row="row" :col="col" />
      </template>
      <template v-else-if="col.formatter" #default="{ row }">
        {{ col.formatter?.(row as Record<string, unknown>) }}
      </template>
    </el-table-column>
    <slot name="actions" />
  </el-table>
</template>
