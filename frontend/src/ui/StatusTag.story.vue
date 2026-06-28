<script setup lang="ts">
/**
 * StatusTag 故事 — 状态标签
 *
 * 演示内置默认 typeMap(设备/工单/告警状态)+ 自定义覆盖。
 */
import StatusTag from './StatusTag.vue'
import type { StatusType } from './StatusTag.vue'
</script>

<template>
  <Story
    title="StatusTag"
    group="basic"
    :layout="{ type: 'grid', width: 100 }"
  >
    <!-- 设备状态(数字 0/1/2) -->
    <Variant title="设备状态(数字)">
      <StatusTag :value="0" label="离线" />
      <StatusTag :value="1" label="在线" />
      <StatusTag :value="2" label="禁用" />
    </Variant>

    <!-- 工单状态(字符串) -->
    <Variant title="工单状态(默认 typeMap)">
      <StatusTag value="PENDING" />
      <StatusTag value="PROCESSING" />
      <StatusTag value="COMPLETED" />
      <StatusTag value="OVERDUE" />
      <StatusTag value="CLOSED" />
    </Variant>

    <!-- 告警级别 -->
    <Variant title="告警级别(默认 typeMap)">
      <StatusTag value="INFO" />
      <StatusTag value="WARN" />
      <StatusTag value="ERROR" />
      <StatusTag value="CRITICAL" />
    </Variant>

    <!-- 自定义 typeMap + label -->
    <Variant title="自定义 typeMap + label" :init-state="() => ({ value: 'DRAFT', label: '草稿文档', typeMap: { DRAFT: 'info', REVIEWING: 'warning', PUBLISHED: 'success' } as Record<string, StatusType> })">
      <template #default="{ state }">
        <StatusTag v-bind="state" />
      </template>
    </Variant>

    <!-- 未知值(走 fallback) -->
    <Variant title="未知值(走 info fallback)">
      <StatusTag value="XYZ_NOT_MAPPED" />
    </Variant>
  </Story>
</template>