<script setup lang="ts">
/**
 * DescriptionList 故事 — 详情描述列表
 */
import DescriptionList from './DescriptionList.vue'

const basicItems = [
  { label: '设备名', value: '主变温度监测' },
  { label: '设备 Key', value: 'TH-MAIN-001' },
  { label: '状态', value: '在线' },
  { label: '健康度', value: 92 }
]

const spanItems = [
  { label: '产品名', value: '国网芯温度传感器' },
  { label: '产品 Key', value: 'sg_temp_sensor' },
  { label: '节点类型', value: '直连设备' },
  { label: '描述', value: '母排/触头/电缆接头无线温度监测', span: 3 },
  { label: '物模型', value: 'properties: [temperature, batteryLevel, location]', span: 3 }
]

const formattingItems = [
  { label: '空值(null)', value: null },
  { label: '空值(undefined)', value: undefined },
  { label: '空字符串', value: '' },
  { label: '数字 0', value: 0 },
  { label: '布尔 false', value: false },
  { label: '正常字符串', value: 'TH-MAIN-001' },
  { label: '日期', value: new Date('2026-06-29T10:30:00') }
]
</script>

<template>
  <Story title="DescriptionList" group="basic" :layout="{ type: 'grid', width: 480 }">
    <!-- 基础 -->
    <Variant title="基础" :init-state="() => ({ items: basicItems })">
      <template #default="{ state }">
        <DescriptionList v-bind="state" />
      </template>
    </Variant>

    <!-- 单列 -->
    <Variant title="单列" :init-state="() => ({ column: 1, items: basicItems })">
      <template #default="{ state }">
        <DescriptionList v-bind="state" />
      </template>
    </Variant>

    <!-- 三列 + 跨行 -->
    <Variant title="三列 + 跨行(span)" :init-state="() => ({ column: 3, items: spanItems })">
      <template #default="{ state }">
        <DescriptionList v-bind="state" />
      </template>
    </Variant>

    <!-- 含标题 -->
    <Variant
      title="含标题"
      :init-state="() => ({ title: '设备基本信息', items: basicItems.slice(0, 2) })"
    >
      <template #default="{ state }">
        <DescriptionList v-bind="state" />
      </template>
    </Variant>

    <!-- value 格式化 -->
    <Variant
      title="value 格式化(null/undefined/Date 等)"
      :init-state="() => ({ column: 2, items: formattingItems })"
    >
      <template #default="{ state }">
        <DescriptionList v-bind="state" />
      </template>
    </Variant>
  </Story>
</template>