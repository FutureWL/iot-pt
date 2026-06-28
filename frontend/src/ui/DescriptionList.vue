<script setup lang="ts">
/**
 * DescriptionList — 描述列表组件
 *
 * 设计目标:统一 6 处 el-descriptions 重复 (dashboard, alert, workorder/Detail, rule/Alert, monitor/Topology, Gis)
 *
 * 使用示例:
 *   <DescriptionList :items="[
 *     { label: '设备名', value: device.deviceName },
 *     { label: '状态', value: device.status, span: 2 }
 *   ]" :column="3" />
 */
interface Item {
  /** 标签文本 */
  label: string
  /** 显示值,null/undefined 显示占位符 */
  value: unknown
  /** 跨列数 */
  span?: number
}

interface Props {
  /** 描述项数组 */
  items: Item[]
  /** 列数,默认 2 */
  column?: number
  /** 可选分组标题 */
  title?: string
}

const props = withDefaults(defineProps<Props>(), {
  column: 2,
  title: ''
})

/** 格式化显示值,null/undefined 显示 '-' */
function formatValue(value: unknown): string {
  if (value === null || value === undefined || value === '') return '-'
  if (value instanceof Date) return value.toLocaleString('zh-CN')
  return String(value)
}
</script>

<template>
  <el-descriptions
    :column="props.column"
    :title="props.title || undefined"
    border
  >
    <el-descriptions-item
      v-for="(item, idx) in props.items"
      :key="idx"
      :label="item.label"
      :span="item.span"
    >
      {{ formatValue(item.value) }}
    </el-descriptions-item>
  </el-descriptions>
</template>
