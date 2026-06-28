<script setup lang="ts">
/**
 * DictSelect 故事 — 字典下拉选择器
 *
 * 注:Histoire 不发真实 HTTP,故事里看到的选项来自 mock。
 * 真实运行时(浏览器 dev 模式)会自动调 GET /system/dict/{dictType}。
 */
import DictSelect from './DictSelect.vue'
</script>

<template>
  <Story title="DictSelect" group="basic" :layout="{ type: 'grid', width: 280 }">
    <!-- 单选 -->
    <Variant
      title="单选"
      :init-state="() => ({ dictType: 'workorder_status' })"
    >
      <template #default="{ state }">
        <DictSelect v-bind="state" />
      </template>
    </Variant>

    <!-- 多选 -->
    <Variant
      title="多选"
      :init-state="() => ({ dictType: 'workorder_status', multiple: true })"
    >
      <template #default="{ state }">
        <DictSelect v-bind="state" />
      </template>
    </Variant>

    <!-- 可搜索 -->
    <Variant
      title="可搜索"
      :init-state="() => ({ dictType: 'workorder_status', filterable: true })"
    >
      <template #default="{ state }">
        <DictSelect v-bind="state" />
      </template>
    </Variant>

    <!-- 禁用 + 默认值 -->
    <Variant
      title="禁用 + 默认值"
      :init-state="() => ({ dictType: 'workorder_status', disabled: true, modelValue: 'COMPLETED' })"
    >
      <template #default="{ state }">
        <DictSelect v-bind="state" />
      </template>
    </Variant>
  </Story>
</template>