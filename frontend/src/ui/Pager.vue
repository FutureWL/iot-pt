<script setup lang="ts">
/**
 * Pager — 分页器封装
 *
 * 设计目标:统一 6+ 处 el-pagination 重复
 *
 * 使用示例:
 *   <Pager v-model:current="query.pageNum" v-model:size="query.pageSize" :total="total" />
 */
interface Props {
  /** 当前页码 */
  current: number
  /** 每页大小 */
  size: number
  /** 总条数 */
  total: number
  /** 每页大小选项,默认 [10, 20, 50, 100] */
  pageSizes?: number[]
  /** 布局,默认 'total, sizes, prev, pager, next, jumper' */
  layout?: string
}

const props = withDefaults(defineProps<Props>(), {
  pageSizes: () => [10, 20, 50, 100],
  layout: 'total, sizes, prev, pager, next, jumper'
})

const emit = defineEmits<{
  'update:current': [p: number]
  'update:size': [s: number]
}>()

function onCurrentChange(p: number): void {
  emit('update:current', p)
}

function onSizeChange(s: number): void {
  emit('update:size', s)
  // Element Plus 切 size 时不会自动回 page 1,业务侧 useTable 已处理
}
</script>

<template>
  <el-pagination
    :current-page="props.current"
    :page-size="props.size"
    :total="props.total"
    :page-sizes="props.pageSizes"
    :layout="props.layout"
    @current-change="onCurrentChange"
    @size-change="onSizeChange"
  />
</template>
