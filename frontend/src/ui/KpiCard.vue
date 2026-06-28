<script setup lang="ts">
/**
 * KpiCard — 关键指标卡片
 *
 * 设计目标:统一 4 处 dashboard 卡片 (dashboard/Index, iot-console, ops/Statistics, screen)
 *
 * 使用示例:
 *   <KpiCard title="在线设备" :value="128" suffix="台" :trend="5" color="primary" />
 */
import { computed } from 'vue'

interface Props {
  /** 卡片标题 */
  title: string
  /** 主数值 */
  value: number | string
  /** 单位(%、个、ms 等) */
  suffix?: string
  /** 趋势百分比(正涨负跌,0 不显示) */
  trend?: number
  /** el-icon 组件名 */
  icon?: string
  /** 主题色 */
  color?: 'primary' | 'success' | 'warning' | 'danger' | 'info'
  /** 加载态 */
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  suffix: '',
  trend: 0,
  icon: '',
  color: 'primary',
  loading: false
})

const showTrendUp = computed<boolean>(() => (props.trend ?? 0) > 0)
const showTrendDown = computed<boolean>(() => (props.trend ?? 0) < 0)
</script>

<template>
  <div
    class="kpi-card"
    :class="[`kpi-card--${props.color}`]"
    :data-loading="props.loading"
  >
    <div class="kpi-card__header">
      <span class="kpi-card__title">{{ props.title }}</span>
      <el-icon
        v-if="props.icon"
        class="kpi-card__icon"
      >
        <component :is="props.icon" />
      </el-icon>
    </div>
    <el-statistic
      class="kpi-card__stat"
      :value="props.value"
      :suffix="props.suffix || undefined"
    />
    <div
      v-if="showTrendUp"
      data-testid="kpi-trend-up"
      class="kpi-card__trend kpi-card__trend--up"
    >
      ↑ {{ props.trend }}%
    </div>
    <div
      v-else-if="showTrendDown"
      data-testid="kpi-trend-down"
      class="kpi-card__trend kpi-card__trend--down"
    >
      ↓ {{ Math.abs(props.trend ?? 0) }}%
    </div>
  </div>
</template>

<style scoped lang="scss">
.kpi-card {
  padding: 16px;
  border-radius: 8px;
  background: var(--iot-bg-card, #fff);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 160px;

  &--primary { border-left: 3px solid var(--iot-color-primary, #409eff); }
  &--success { border-left: 3px solid var(--iot-color-success, #67c23a); }
  &--warning { border-left: 3px solid var(--iot-color-warning, #e6a23c); }
  &--danger  { border-left: 3px solid var(--iot-color-danger, #f56c6c); }
  &--info    { border-left: 3px solid var(--iot-color-info, #909399); }

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__title {
    font-size: 13px;
    color: var(--iot-text-regular, #606266);
  }

  &__icon {
    font-size: 18px;
    color: var(--iot-text-placeholder, #c0c4cc);
  }

  &__trend {
    font-size: 12px;
    font-weight: 500;

    &--up { color: var(--iot-color-success, #67c23a); }
    &--down { color: var(--iot-color-danger, #f56c6c); }
  }
}
</style>
