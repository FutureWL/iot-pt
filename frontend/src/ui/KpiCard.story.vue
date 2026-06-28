<script setup lang="ts">
/**
 * KpiCard 故事 — 关键指标卡片
 *
 * 使用 <Story> + <Variant> 描述组件的不同状态。
 * 每个 <Variant> 是一个独立可交互的演示场景。
 */
import KpiCard from './KpiCard.vue'

function initState() {
  return {
    title: '在线设备',
    value: 128,
    suffix: '台',
    icon: 'Monitor',
    color: 'primary',
    trend: 5
  }
}
</script>

<template>
  <Story
    title="KpiCard"
    group="basic"
    :layout="{ type: 'grid', width: 200 }"
  >
    <!-- 默认(主色 + 上升趋势) -->
    <Variant title="默认 Primary" :init-state="initState">
      <template #default="{ state }">
        <KpiCard
          :title="state.title"
          :value="state.value"
          :suffix="state.suffix"
          :icon="state.icon"
          :color="state.color"
          :trend="state.trend"
        />
      </template>
    </Variant>

    <!-- 上升趋势(绿色) -->
    <Variant title="上升趋势 Success" :init-state="() => ({ title: '今日告警', value: 23, suffix: '条', color: 'warning', trend: 12 })">
      <template #default="{ state }">
        <KpiCard v-bind="state" />
      </template>
    </Variant>

    <!-- 下降趋势(红色) -->
    <Variant title="下降趋势 Danger" :init-state="() => ({ title: '超时工单', value: 3, suffix: '单', color: 'danger', trend: -25 })">
      <template #default="{ state }">
        <KpiCard v-bind="state" />
      </template>
    </Variant>

    <!-- 加载中 -->
    <Variant title="加载中" :init-state="() => ({ title: '系统负载', value: 0, color: 'info', loading: true })">
      <template #default="{ state }">
        <KpiCard v-bind="state" />
      </template>
    </Variant>
  </Story>
</template>