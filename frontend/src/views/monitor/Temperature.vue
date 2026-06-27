<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { Refresh, Histogram } from '@element-plus/icons-vue'
import * as echarts from 'echarts/core'
import { LineChart, HeatmapChart } from 'echarts/charts'
import {
  GridComponent, TooltipComponent, LegendComponent, TitleComponent, VisualMapComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import {
  getTemperatureStats,
  listTemperaturePoints,
  getTemperatureHistory,
  type TemperaturePointVO,
  type TemperatureStatsVO,
  type TemperatureLocation
} from '@/api/monitor/temperature'

echarts.use([LineChart, HeatmapChart, GridComponent, TooltipComponent, LegendComponent, TitleComponent, VisualMapComponent, CanvasRenderer])

const loading = ref(false)
const stats = ref<TemperatureStatsVO>({ max: 0, avg: 0, min: 0, alertCount: 0, sensorCount: 0 })
const points = ref<TemperaturePointVO[]>([])
const locationFilter = ref<TemperatureLocation | ''>('')

const ALERT_TEMP = 75  // 母排告警阈值 ℃(示意)

let chart: echarts.ECharts | null = null
const chartRef = ref<HTMLDivElement>()

function renderChart() {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)

  // 取第一台有历史的传感器画曲线
  const first = points.value[0]
  if (!first) {
    chart.clear()
    return
  }

  // 同步拉取历史(此处直接复用 stats 展示,真实历史曲线需等后端接口)
  getTemperatureHistory(first.sensorId, '1h').then((res: any) => {
    const data = res.data ?? []
    chart?.setOption({
      tooltip: { trigger: 'axis' },
      grid: { top: 36, right: 24, bottom: 32, left: 48 },
      legend: { data: ['温度 (℃)', '告警阈值'], top: 0 },
      xAxis: {
        type: 'category', data: data.map((d: any) => d.ts?.slice(11, 19) ?? ''),
        axisLine: { lineStyle: { color: '#dcdfe6' } },
        axisLabel: { color: '#909399' }
      },
      yAxis: {
        type: 'value', name: '℃', min: 0,
        splitLine: { lineStyle: { color: '#ebeef5' } },
        axisLabel: { color: '#909399' }
      },
      series: [
        {
          name: '温度 (℃)', type: 'line', smooth: true, showSymbol: false,
          data: data.map((d: any) => d.temperature),
          itemStyle: { color: '#f56c6c' },
          areaStyle: { color: 'rgba(245,108,108,0.15)' },
          markLine: { data: [{ yAxis: ALERT_TEMP, name: '告警阈值' }], symbol: 'none', lineStyle: { color: '#e6a23c', type: 'dashed' } }
        }
      ]
    })
  })
}

async function load() {
  loading.value = true
  try {
    const [sRes, pRes]: any[] = await Promise.all([
      getTemperatureStats(),
      listTemperaturePoints(locationFilter.value ? { location: locationFilter.value } : undefined)
    ])
    stats.value = sRes.data ?? stats.value
    points.value = pRes.data ?? []
    await nextTick()
    renderChart()
  } finally {
    loading.value = false
  }
}

function onLocationChange() { load() }
function handleResize() { chart?.resize() }

onMounted(() => { load(); window.addEventListener('resize', handleResize) })
onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose(); chart = null
})
</script>

<template>
  <div class="page-container temp-page" v-loading="loading">
    <div class="page-header">
      <h2 class="page-title">温度监测</h2>
      <el-select v-model="locationFilter" placeholder="全部位置" clearable style="width: 160px"
        @change="onLocationChange">
        <el-option label="母排" value="母排" />
        <el-option label="触头" value="触头" />
        <el-option label="电缆接头" value="电缆接头" />
      </el-select>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <!-- 4 卡统计 -->
    <el-row :gutter="16" class="mb-16">
      <el-col :xs="12" :sm="6">
        <div class="temp-card temp-max">
          <div class="temp-label">最高温度</div>
          <div class="temp-num">{{ stats.max.toFixed(1) }} <span class="temp-unit">℃</span></div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="temp-card temp-avg">
          <div class="temp-label">平均温度</div>
          <div class="temp-num">{{ stats.avg.toFixed(1) }} <span class="temp-unit">℃</span></div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="temp-card temp-min">
          <div class="temp-label">最低温度</div>
          <div class="temp-num">{{ stats.min.toFixed(1) }} <span class="temp-unit">℃</span></div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="temp-card temp-alert">
          <div class="temp-label">超温点</div>
          <div class="temp-num">{{ stats.alertCount }}</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :xs="24" :md="16">
        <div class="page-card">
          <h3 class="card-title">温度趋势(最近 1 小时)</h3>
          <div ref="chartRef" class="chart-area"></div>
          <el-empty v-if="points.length === 0" description="暂无温度传感器数据" />
        </div>
      </el-col>
      <el-col :xs="24" :md="8">
        <div class="page-card">
          <h3 class="card-title">超温点列表</h3>
          <el-table :data="points.filter(p => p.temperature >= ALERT_TEMP)" stripe empty-text="无超温点">
            <el-table-column prop="sensorId" label="传感器" width="100" />
            <el-table-column prop="location" label="位置" width="100" />
            <el-table-column label="温度" width="100">
              <template #default="{ row }">
                <span class="text-danger font-semibold">{{ row.temperature.toFixed(1) }} ℃</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>
    </el-row>

    <div class="page-card mt-16">
      <h3 class="card-title">测点列表</h3>
      <el-table :data="points" stripe empty-text="暂无测点">
        <el-table-column prop="sensorId" label="传感器 ID" width="140" />
        <el-table-column prop="deviceKey" label="设备 Key" width="140" />
        <el-table-column prop="location" label="位置" width="100" />
        <el-table-column label="温度(℃)" width="110">
          <template #default="{ row }">
            <span :class="{ 'text-danger': row.temperature >= ALERT_TEMP }">
              {{ row.temperature.toFixed(1) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="batteryLevel" label="电量(%)" width="100" />
        <el-table-column prop="ts" label="采集时间" min-width="170" />
      </el-table>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.temp-page { background: var(--iot-bg-page); }
.page-header { display: flex; align-items: center; gap: $spacing-12; margin-bottom: $spacing-16; .page-title { margin: 0; flex: 1; } }
.mb-16 { margin-bottom: $spacing-16; }
.mt-16 { margin-top: $spacing-16; }

.temp-card {
  background: var(--iot-bg-card); border-radius: $radius-large; padding: $spacing-20 $spacing-16;
  text-align: center; box-shadow: var(--iot-shadow-light); margin-bottom: $spacing-12;
}
.temp-label { font-size: $font-size-extra-small; color: var(--iot-text-secondary); margin-bottom: $spacing-8; }
.temp-num { font-size: 36px; font-weight: $font-weight-bold; font-family: var(--iot-font-family-code); color: var(--iot-text-primary); }
.temp-unit { font-size: $font-size-medium; color: var(--iot-text-secondary); font-weight: $font-weight-normal; }
.temp-max { background: linear-gradient(135deg, var(--iot-color-danger-light) 0%, #fff 100%); }
.temp-max .temp-num { color: var(--iot-color-danger); }
.temp-avg .temp-num { color: var(--iot-color-warning); }
.temp-min .temp-num { color: var(--iot-color-primary); }
.temp-alert .temp-num { color: var(--iot-color-danger); }

.card-title {
  font-size: $font-size-medium; margin: 0 0 $spacing-16;
  color: var(--iot-text-primary); display: flex; align-items: center; gap: $spacing-8;
  &::before { content: ''; display: block; width: 3px; height: 14px; background: var(--iot-color-primary); }
}
.chart-area { width: 100%; height: 280px; }
</style>