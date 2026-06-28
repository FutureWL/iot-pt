<script setup lang="ts">
/**
 * StatusTag — 状态标签组件
 *
 * 设计目标:统一 11+ 处 statusMap 内联 (device/List.vue、alert/Center.vue 等)
 *
 * 内置默认 typeMap 覆盖常见状态值,业务可自定义 typeMap 覆盖
 *
 * 使用示例:
 *   <StatusTag :value="device.status" />          <!-- 数字 0/1/2 -->
 *   <StatusTag :value="order.status" />           <!-- 字符串 PENDING/PROCESSING/... -->
 *   <StatusTag :value="row.status" label="自定义" :type-map="myMap" />
 */
import { computed } from 'vue'

/** StatusTag 颜色类型(可被 StatusTag.typeMap 覆盖) */
export type StatusType = 'success' | 'warning' | 'danger' | 'info' | 'primary'

interface Props {
  /** 状态值:字符串(枚举)或数字(状态码) */
  value: string | number
  /** 显示文本,缺省直接显示 value */
  label?: string
  /** 自定义类型映射,key 为 value 的字符串形式 */
  typeMap?: Record<string, StatusType>
  /** el-tag 尺寸 */
  size?: 'small' | 'default' | 'large'
}

/** 内置默认映射 — 覆盖工单/告警/设备常见状态 */
const DEFAULT_TYPE_MAP: Record<string, StatusType> = {
  // 设备状态(数字)
  '0': 'info',     // 离线
  '1': 'success',  // 在线
  '2': 'danger',   // 禁用
  // 工单状态(字符串)
  PENDING: 'warning',
  PROCESSING: 'primary',
  COMPLETED: 'success',
  OVERDUE: 'danger',
  CLOSED: 'info',
  // 告警级别
  CRITICAL: 'danger',
  HIGH: 'warning',
  MEDIUM: 'warning',
  LOW: 'info'
}

const props = withDefaults(defineProps<Props>(), {
  label: '',
  typeMap: () => ({}),
  size: 'small'
})

const displayLabel = computed<string>(() => props.label || String(props.value))

const displayType = computed<StatusType>(() => {
  const key = String(props.value)
  if (props.typeMap[key]) return props.typeMap[key]!
  return DEFAULT_TYPE_MAP[key] ?? 'info'
})
</script>

<template>
  <el-tag :type="displayType" :size="props.size">
    {{ displayLabel }}
  </el-tag>
</template>
