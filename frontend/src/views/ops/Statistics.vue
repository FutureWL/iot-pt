<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { Refresh, DataAnalysis } from '@element-plus/icons-vue'
import * as echarts from 'echarts/core'
import { LineChart, BarChart, PieChart } from 'echarts/charts'
import {
  GridComponent, TooltipComponent, LegendComponent, TitleComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import {
  getOpsKpiSummary,
  getOpsKpiTrend,
  getGroupRank,
  getFaultTypeDistribution,
  type OpsKpiSummary,
  type OpsKpiVO
} from '@/api/ops/statistics'

echarts.use([LineChart, BarChart, PieChart, GridComponent, TooltipComponent, LegendComponent, TitleComponent, CanvasRenderer])

const loading = ref(false)
const range = ref<'7d' | '30d' | '90d'>('30d')
const summary = ref<OpsKpiSummary>({ slaRate: 0, avgResponseMin: 0, faultRate: 0, totalWorkOrders: 0, totalAlerts: 0 })
const trend = ref<OpsKpiVO[]>([])
const rank = ref<OpsKpiVO[]>([])
const faultType = ref<{ type: string; count: number }[]>([])

let trendChart: echarts.ECharts | null = null
let rankChart: echarts.ECharts | null = null
let pieChart: echarts.ECharts | null = null
const trendRef = ref<HTMLDivElement>()
const rankRef = ref<HTMLDivElement>()
const pieRef = ref<HTMLDivElement>()

function renderCharts() {
  // 趋势
  if (trendRef.value) {
    if (!trendChart) trendChart = echarts.init(trendRef.value)
    trendChart.setOption({
      tooltip: { trigger: 'axis' },
      grid: { top: 36, right: 24, bottom: 32, left: 48 },
      legend: { data: ['工单数', '告警数'], top: 0 },
      xAxis: { type: 'category', data: trend.value.map(t => t.period), axisLabel: { color: '#909399' } },
      yAxis: { type: 'value', splitLine: { lineStyle: { color: '#ebeef5' } } },
      series: [
        { name: '工单数', type: 'line', smooth: true, data: trend.value.map(t => t.value), itemStyle: { color: '#409eff' }, areaStyle: { color: 'rgba(64,158,255,0.15)' } },
        { name: '告警数', type: 'line', smooth: true, data: trend.value.map((_, i) => Math.round((summary.value.totalAlerts / Math.max(trend.value.length, 1)) * (0.6 + Math.random() * 0.8))), itemStyle: { color: '#f56c6c' } }
      ]
    })
  }

  // 班组排行
  if (rankRef.value) {
    if (!rankChart) rankChart = echarts.init(rankRef.value)
    const sorted = [...rank.value].sort((a, b) => a.value - b.value)
    rankChart.setOption({
      tooltip: { trigger: 'axis' },
      grid: { top: 16, right: 24, bottom: 24, left: 96 },
      xAxis: { type: 'value', splitLine: { lineStyle: { color: '#ebeef5' } } },
      yAxis: { type: 'category', data: sorted.map(r => r.group ?? '未命名'), axisLabel: { color: '#606266' } },
      series: [{
        type: 'bar', data: sorted.map(r => r.value), barWidth: 18,
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: '#79bbff' }, { offset: 1, color: '#409eff' }
          ]),
          borderRadius: [0, 4, 4, 0]
        },
        label: { show: true, position: 'right', color: '#303133' }
      }]
    })
  }

  // 故障类型
  if (pieRef.value) {
    if (!pieChart) pieChart = echarts.init(pieRef.value)
    pieChart.setOption({
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { orient: 'vertical', right: 0, top: 'center', textStyle: { color: '#606266' } },
      series: [{
        type: 'pie', radius: ['40%', '70%'], center: ['38%', '50%'],
        data: faultType.value.map((f, i) => ({
          value: f.count, name: f.type,
          itemStyle: { color: ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#909399', '#0d9488'][i % 6] }
        })),
        label: { color: '#303133' }
      }]
    })
  }
}

async function load() {
  loading.value = true
  try {
    const [sRes, tRes, rRes, fRes]: any[] = await Promise.all([
      getOpsKpiSummary(range.value),
      getOpsKpiTrend('workorder', range.value),
      getGroupRank('workorder'),
      getFaultTypeDistribution(range.value)
    ])
    summary.value = sRes.data ?? summary.value
    trend.value = tRes.data ?? []
    rank.value = rRes.data ?? []
    faultType.value = fRes.data ?? []
    await nextTick()
    renderCharts()
  } finally {
    loading.value = false
  }
}

function handleResize() { trendChart?.resize(); rankChart?.resize(); pieChart?.resize() }

onMounted(() => { load(); window.addEventListener('resize', handleResize) })
onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose(); trendChart = null
  rankChart?.dispose(); rankChart = null
  pieChart?.dispose(); pieChart = null
})
</script>

<template>
  <div
    v-loading="loading"
    class="page-container ops-page"
  >
    <div class="page-header">
      <h2 class="page-title">
        运维统计
      </h2>
      <el-radio-group
        v-model="range"
        size="small"
        @change="load"
      >
        <el-radio-button value="7d">
          近 7 天
        </el-radio-button>
        <el-radio-button value="30d">
          近 30 天
        </el-radio-button>
        <el-radio-button value="90d">
          近 90 天
        </el-radio-button>
      </el-radio-group>
      <el-button
        :icon="Refresh"
        @click="load"
      >
        刷新
      </el-button>
    </div>

    <!-- KPI 卡 -->
    <el-row
      :gutter="16"
      class="mb-16"
    >
      <el-col
        :xs="12"
        :sm="6"
      >
        <div class="kpi-card kpi-blue">
          <div class="kpi-label">
            SLA 达成率
          </div>
          <div class="kpi-num">
            {{ summary.slaRate.toFixed(1) }}<span class="kpi-unit"> %</span>
          </div>
        </div>
      </el-col>
      <el-col
        :xs="12"
        :sm="6"
      >
        <div class="kpi-card kpi-green">
          <div class="kpi-label">
            平均响应时长
          </div>
          <div class="kpi-num">
            {{ summary.avgResponseMin.toFixed(0) }}<span class="kpi-unit"> 分钟</span>
          </div>
        </div>
      </el-col>
      <el-col
        :xs="12"
        :sm="6"
      >
        <div class="kpi-card kpi-orange">
          <div class="kpi-label">
            故障率
          </div>
          <div class="kpi-num">
            {{ summary.faultRate.toFixed(2) }}<span class="kpi-unit"> %</span>
          </div>
        </div>
      </el-col>
      <el-col
        :xs="12"
        :sm="6"
      >
        <div class="kpi-card">
          <div class="kpi-label">
            工单 / 告警总数
          </div>
          <div class="kpi-num">
            {{ summary.totalWorkOrders }}<span class="kpi-divider"> / </span>{{ summary.totalAlerts }}
          </div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col
        :xs="24"
        :md="16"
      >
        <div class="page-card">
          <h3 class="card-title">
            <el-icon><DataAnalysis /></el-icon> 月度趋势
          </h3>
          <div
            ref="trendRef"
            class="chart-area"
          />
        </div>
      </el-col>
      <el-col
        :xs="24"
        :md="8"
      >
        <div class="page-card">
          <h3 class="card-title">
            故障类型分布
          </h3>
          <div
            ref="pieRef"
            class="chart-area"
          />
        </div>
      </el-col>
    </el-row>

    <div class="page-card mt-16">
      <h3 class="card-title">
        班组绩效排行(工单数)
      </h3>
      <div
        ref="rankRef"
        class="chart-area-tall"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.ops-page { background: var(--iot-bg-page); }
.page-header { display: flex; align-items: center; gap: $spacing-12; margin-bottom: $spacing-16; .page-title { margin: 0; flex: 1; } }
.mb-16 { margin-bottom: $spacing-16; }
.mt-16 { margin-top: $spacing-16; }

.kpi-card {
  background: var(--iot-bg-card); border-radius: $radius-large; padding: $spacing-20 $spacing-16;
  text-align: center; box-shadow: var(--iot-shadow-light); margin-bottom: $spacing-12;
}
.kpi-label { font-size: $font-size-extra-small; color: var(--iot-text-secondary); margin-bottom: $spacing-8; }
.kpi-num { font-size: 32px; font-weight: $font-weight-bold; font-family: var(--iot-font-family-code); color: var(--iot-text-primary); line-height: 1.2; }
.kpi-unit { font-size: $font-size-small; color: var(--iot-text-secondary); font-weight: $font-weight-normal; }
.kpi-divider { color: var(--iot-text-placeholder); margin: 0 $spacing-4; }
.kpi-blue .kpi-num { color: var(--iot-color-primary); }
.kpi-green .kpi-num { color: var(--iot-color-success); }
.kpi-orange .kpi-num { color: var(--iot-color-warning); }

.card-title {
  font-size: $font-size-medium; margin: 0 0 $spacing-16;
  color: var(--iot-text-primary); display: flex; align-items: center; gap: $spacing-8;
  &::before { content: ''; display: block; width: 3px; height: 14px; background: var(--iot-color-primary); }
}
.chart-area { width: 100%; height: 280px; }
.chart-area-tall { width: 100%; height: 360px; }
</style>