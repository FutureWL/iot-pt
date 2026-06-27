<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick, computed } from 'vue'
import { useRouter } from 'vue-router'
import { Monitor, Connection, Warning, Box, Search, Refresh } from '@element-plus/icons-vue'
import * as echarts from 'echarts/core'
import { BarChart } from 'echarts/charts'
import {
  GridComponent, TooltipComponent, LegendComponent, TitleComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import {
  getOverviewStats,
  getProductDistribution,
  listOverviewDevices,
  type DeviceOverviewStats,
  type ProductDistribution,
  type DeviceOverviewVO
} from '@/api/device/overview'

echarts.use([BarChart, GridComponent, TooltipComponent, LegendComponent, TitleComponent, CanvasRenderer])

const router = useRouter()
const loading = ref(false)
const stats = ref<DeviceOverviewStats>({
  total: 0, online: 0, offline: 0, fault: 0, warning: 0, healthScore: 0
})
const distribution = ref<ProductDistribution[]>([])
const devices = ref<DeviceOverviewVO[]>([])
const keyword = ref('')

const healthScoreColor = computed(() => {
  const s = stats.value.healthScore
  if (s >= 90) return 'var(--iot-color-success)'
  if (s >= 75) return 'var(--iot-color-primary)'
  if (s >= 60) return 'var(--iot-color-warning)'
  return 'var(--iot-color-danger)'
})

const healthScoreLabel = computed(() => {
  const s = stats.value.healthScore
  if (s >= 90) return '优秀'
  if (s >= 75) return '良好'
  if (s >= 60) return '一般'
  return '异常'
})

let chart: echarts.ECharts | null = null
const chartRef = ref<HTMLDivElement>()

function renderChart() {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  chart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { top: 24, right: 24, bottom: 32, left: 48 },
    xAxis: {
      type: 'category',
      data: distribution.value.map(d => d.productName),
      axisLine: { lineStyle: { color: '#dcdfe6' } },
      axisLabel: { color: '#606266', interval: 0, rotate: distribution.value.length > 6 ? 30 : 0 }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#ebeef5' } },
      axisLabel: { color: '#909399' }
    },
    series: [{
      type: 'bar',
      data: distribution.value.map(d => d.count),
      barWidth: 28,
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#409eff' },
          { offset: 1, color: '#79bbff' }
        ]),
        borderRadius: [4, 4, 0, 0]
      },
      label: { show: true, position: 'top', color: '#303133', fontSize: 12 }
    }]
  })
}

async function load() {
  loading.value = true
  try {
    const [sRes, dRes, lRes]: any[] = await Promise.all([
      getOverviewStats(),
      getProductDistribution(),
      listOverviewDevices({ keyword: keyword.value || undefined })
    ])
    stats.value = sRes.data ?? stats.value
    distribution.value = dRes.data ?? []
    devices.value = lRes.data ?? []
    await nextTick()
    renderChart()
  } finally {
    loading.value = false
  }
}

function onSearch() { load() }
function onReset() { keyword.value = ''; load() }
function goList() { router.push('/device/list') }
function goDetail(id: number) { router.push(`/device/list?deviceId=${id}`) }

function handleResize() { chart?.resize() }
onMounted(() => { load(); window.addEventListener('resize', handleResize) })
onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div class="page-container overview-page" v-loading="loading">
    <div class="page-header">
      <h2 class="page-title">设备总览</h2>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <!-- 顶部 4 卡片(原 5 卡片中"产品"并入图表,加健康度评分) -->
    <el-row :gutter="16" class="mb-16">
      <el-col :xs="12" :sm="6">
        <div class="stat-card stat-blue clickable" @click="goList">
          <el-icon :size="28"><Monitor /></el-icon>
          <div class="stat-num">{{ stats.total }}</div>
          <div class="stat-label">设备总数</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="stat-card stat-green clickable" @click="goList">
          <el-icon :size="28"><Connection /></el-icon>
          <div class="stat-num">{{ stats.online }}</div>
          <div class="stat-label">在线设备</div>
          <div class="stat-rate">
            在线率 {{ stats.total ? Math.round(stats.online / stats.total * 100) : 0 }}%
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="stat-card stat-red clickable" @click="router.push('/alert/center')">
          <el-icon :size="28"><Warning /></el-icon>
          <div class="stat-num">{{ stats.fault }}</div>
          <div class="stat-label">故障设备</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="stat-card" :style="{ background: `linear-gradient(135deg, ${healthScoreColor}15 0%, #fff 100%)`, color: healthScoreColor }">
          <el-icon :size="28"><Box /></el-icon>
          <div class="stat-num" :style="{ color: healthScoreColor }">{{ stats.healthScore }}</div>
          <div class="stat-label">健康度评分 · {{ healthScoreLabel }}</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :xs="24" :md="14">
        <div class="page-card">
          <h3 class="card-title">产品 / 设备分布</h3>
          <div ref="chartRef" class="chart-area"></div>
          <el-empty v-if="distribution.length === 0" description="暂无产品数据" />
        </div>
      </el-col>
      <el-col :xs="24" :md="10">
        <div class="page-card">
          <h3 class="card-title">终端列表</h3>
          <div class="page-toolbar">
            <el-input v-model="keyword" placeholder="设备名 / Key" clearable style="width: 200px"
              :prefix-icon="Search" @keyup.enter="onSearch" />
            <el-button type="primary" @click="onSearch">查询</el-button>
            <el-button @click="onReset">重置</el-button>
          </div>
          <el-table :data="devices" stripe max-height="380" empty-text="暂无设备">
            <el-table-column prop="deviceName" label="名称" min-width="120" show-overflow-tooltip />
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : row.status === 2 ? 'danger' : 'info'" size="small">
                  {{ row.status === 1 ? '在线' : row.status === 2 ? '禁用' : '离线' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="健康度" width="100">
              <template #default="{ row }">
                <el-progress :percentage="row.healthScore ?? 0" :stroke-width="6" :show-text="false"
                  :color="(row.healthScore ?? 0) >= 75 ? '#67c23a' : (row.healthScore ?? 0) >= 60 ? '#e6a23c' : '#f56c6c'" />
                <span class="text-secondary text-xs">{{ row.healthScore ?? 0 }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="goDetail(row.id)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.overview-page { background: var(--iot-bg-page); }
.page-header { display: flex; align-items: center; gap: $spacing-12; margin-bottom: $spacing-16; .page-title { margin: 0; flex: 1; } }
.mb-16 { margin-bottom: $spacing-16; }
.stat-card {
  background: var(--iot-bg-card);
  border-radius: $radius-large;
  padding: $spacing-20 $spacing-16;
  text-align: center;
  box-shadow: var(--iot-shadow-light);
  display: flex; flex-direction: column; align-items: center; gap: $spacing-4;
  margin-bottom: $spacing-12;
  transition: transform $transition-base, box-shadow $transition-base;
  &.clickable { cursor: pointer; &:hover { transform: translateY(-2px); box-shadow: var(--iot-shadow-md); } }
}
.stat-num { font-size: $font-size-huge; font-weight: $font-weight-semibold; color: var(--iot-text-primary); font-family: var(--iot-font-family-code); }
.stat-label { font-size: $font-size-extra-small; color: var(--iot-text-secondary); }
.stat-rate { font-size: $font-size-extra-small; color: var(--iot-color-success); margin-top: $spacing-2; }
.stat-blue { color: var(--iot-color-primary); background: linear-gradient(135deg, var(--iot-color-primary-light-9) 0%, #fff 100%); }
.stat-green { color: var(--iot-color-success); background: linear-gradient(135deg, #f0f9eb 0%, #fff 100%); }
.stat-red { color: var(--iot-color-danger); background: linear-gradient(135deg, var(--iot-color-danger-light) 0%, #fff 100%); }

.card-title {
  font-size: $font-size-medium; margin: 0 0 $spacing-16;
  color: var(--iot-text-primary); display: flex; align-items: center; gap: $spacing-8;
  &::before { content: ''; display: block; width: 3px; height: 14px; background: var(--iot-color-primary); }
}
.chart-area { width: 100%; height: 320px; }
</style>