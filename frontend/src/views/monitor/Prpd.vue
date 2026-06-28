<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { Refresh, Cpu } from '@element-plus/icons-vue'
import * as echarts from 'echarts/core'
import { ScatterChart, LineChart } from 'echarts/charts'
import {
  GridComponent, TooltipComponent, LegendComponent, TitleComponent, VisualMapComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { getLatestPrpd, type PrpdResultVO } from '@/api/monitor/prpd'

echarts.use([ScatterChart, LineChart, GridComponent, TooltipComponent, LegendComponent, TitleComponent, VisualMapComponent, CanvasRenderer])

const loading = ref(false)
const deviceId = ref<number>(1)
const result = ref<PrpdResultVO | null>(null)

const dischargeTypeColor: Record<string, string> = {
  '电晕': '#409eff',
  '沿面': '#e6a23c',
  '内部': '#f56c6c',
  '悬浮': '#0d9488',
  '未识别': '#909399'
}

let mainChart: echarts.ECharts | null = null
const mainRef = ref<HTMLDivElement>()

function renderChart() {
  if (!mainRef.value || !result.value) return
  if (!mainChart) mainChart = echarts.init(mainRef.value)
  const points = result.value.points
  const color = dischargeTypeColor[result.value.dischargeType] ?? '#909399'

  mainChart.setOption({
    tooltip: {
      trigger: 'item',
      formatter: (p: any) => `相位 ${p.value[0].toFixed(1)}°<br/>幅值 ${p.value[1].toFixed(1)} dB<br/>脉冲 ${p.value[2]}`
    },
    grid: { top: 32, right: 24, bottom: 36, left: 56 },
    legend: { data: [`${result.value.dischargeType}放电 (${points.length} 点)`], top: 0 },
    xAxis: {
      type: 'value', name: '相位 φ (°)', min: 0, max: 360, nameLocation: 'middle', nameGap: 24,
      splitLine: { lineStyle: { color: '#ebeef5' } },
      axisLabel: { color: '#909399' }
    },
    yAxis: {
      type: 'value', name: '幅值 q (dB)', nameLocation: 'middle', nameGap: 40,
      splitLine: { lineStyle: { color: '#ebeef5' } },
      axisLabel: { color: '#909399' }
    },
    visualMap: {
      show: false, dimension: 2, min: 1, max: Math.max(...points.map(p => p.pulseCount), 10),
      inRange: { color: ['#79bbff', color, '#f56c6c'] }
    },
    series: [{
      type: 'scatter', name: result.value.dischargeType,
      symbolSize: (val: number[]) => Math.min(20, Math.max(3, val[2] / 5)),
      data: points.map(p => [p.phase, p.amplitude, p.pulseCount]),
      itemStyle: { opacity: 0.7 }
    }]
  })
}

async function load() {
  loading.value = true
  try {
    const res: any = await getLatestPrpd(deviceId.value)
    result.value = res.data ?? null
    await nextTick()
    renderChart()
  } finally {
    loading.value = false
  }
}

function handleResize() { mainChart?.resize() }
onMounted(() => { load(); window.addEventListener('resize', handleResize) })
onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  mainChart?.dispose(); mainChart = null
})
</script>

<template>
  <div
    v-loading="loading"
    class="page-container prpd-page"
  >
    <div class="page-header">
      <h2 class="page-title">
        PRPD 图谱
      </h2>
      <div class="header-right">
        <el-input-number
          v-model="deviceId"
          :min="1"
          :max="99999"
          controls-position="right"
          style="width: 140px"
        />
        <el-button
          :icon="Refresh"
          type="primary"
          @click="load"
        >
          刷新
        </el-button>
      </div>
    </div>

    <!-- 识别结果 -->
    <div class="page-card mb-16">
      <el-row
        :gutter="16"
        align="middle"
      >
        <el-col
          :xs="12"
          :sm="6"
        >
          <div class="result-item">
            <div class="result-label">
              设备
            </div>
            <div class="result-value text-primary">
              {{ result?.deviceName ?? '—' }}
            </div>
          </div>
        </el-col>
        <el-col
          :xs="12"
          :sm="6"
        >
          <div class="result-item">
            <div class="result-label">
              采集时间
            </div>
            <div class="result-value">
              {{ result?.collectedAt ?? '—' }}
            </div>
          </div>
        </el-col>
        <el-col
          :xs="12"
          :sm="6"
        >
          <div class="result-item">
            <div class="result-label">
              放电类型识别
            </div>
            <div class="result-value">
              <el-tag
                :color="dischargeTypeColor[result?.dischargeType ?? '未识别']"
                effect="dark"
                size="large"
              >
                {{ result?.dischargeType ?? '—' }}
              </el-tag>
            </div>
          </div>
        </el-col>
        <el-col
          :xs="12"
          :sm="6"
        >
          <div class="result-item">
            <div class="result-label">
              识别置信度
            </div>
            <div class="result-value">
              <el-progress
                :percentage="Math.round((result?.confidence ?? 0) * 100)"
                :stroke-width="10"
                :color="(result?.confidence ?? 0) >= 0.8 ? '#67c23a' : (result?.confidence ?? 0) >= 0.5 ? '#e6a23c' : '#f56c6c'"
              />
            </div>
          </div>
        </el-col>
      </el-row>
    </div>

    <!-- PRPD 散点主图 -->
    <div class="page-card">
      <h3 class="card-title">
        <el-icon><Cpu /></el-icon>
        相位 - 幅值 - 脉冲数 散点图 (φ-q-n)
      </h3>
      <div
        ref="mainRef"
        class="chart-area-large"
      />
      <el-empty
        v-if="!result"
        description="暂无 PRPD 数据,请选择有效设备"
      />
    </div>

    <!-- 历史对比(占位区) -->
    <div class="page-card mt-16">
      <h3 class="card-title">
        历史趋势叠加
      </h3>
      <el-alert
        type="info"
        :closable="false"
        show-icon
      >
        历史趋势叠加需要后端 PRPD 历史接口(`/monitor/prpd/history`)就绪。当前 API 骨架已就绪,
        后端实现后此处会自动启用对比图层(用不同色阶叠加展示 7 天内同设备 PRPD 演变)。
      </el-alert>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.prpd-page { background: var(--iot-bg-page); }
.page-header { display: flex; align-items: center; gap: $spacing-12; margin-bottom: $spacing-16; .page-title { margin: 0; flex: 1; } }
.header-right { display: flex; gap: $spacing-8; }
.mb-16 { margin-bottom: $spacing-16; }
.mt-16 { margin-top: $spacing-16; }

.result-item { padding: $spacing-8 0; }
.result-label { font-size: $font-size-extra-small; color: var(--iot-text-secondary); margin-bottom: $spacing-4; }
.result-value { font-size: $font-size-medium; font-weight: $font-weight-medium; color: var(--iot-text-primary); }

.card-title {
  font-size: $font-size-medium; margin: 0 0 $spacing-16;
  color: var(--iot-text-primary); display: flex; align-items: center; gap: $spacing-8;
  &::before { content: ''; display: block; width: 3px; height: 14px; background: var(--iot-color-primary); }
}
.chart-area-large { width: 100%; height: 480px; }
</style>