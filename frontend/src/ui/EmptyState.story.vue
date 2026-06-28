<script setup lang="ts">
/**
 * EmptyState 故事 — 空状态占位
 */
import EmptyState from './EmptyState.vue'
</script>

<template>
  <Story title="EmptyState" group="basic" :layout="{ type: 'grid', width: 280 }">
    <!-- 基础 -->
    <Variant title="基础" :init-state="() => ({ text: '暂无数据' })">
      <template #default="{ state }">
        <EmptyState v-bind="state" />
      </template>
    </Variant>

    <!-- 带描述 + 自定义图标 -->
    <Variant
      title="带描述 + 自定义图标"
      :init-state="() => ({ text: '暂无设备', description: '点击右上角新建设备按钮添加', image: '🛰️' })"
    >
      <template #default="{ state }">
        <EmptyState v-bind="state" />
      </template>
    </Variant>

    <!-- 小尺寸 -->
    <Variant title="小尺寸" :init-state="() => ({ text: '无匹配结果', imageSize: 60 })">
      <template #default="{ state }">
        <EmptyState v-bind="state" />
      </template>
    </Variant>
  </Story>
</template>