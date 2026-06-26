<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import * as echarts from 'echarts'
import { getDashboardSummary, type DashboardSummary } from '@/api/dashboard'
import { getRealtime, type RealtimeDevice } from '@/api/data/realtime'
import { getHistory } from '@/api/data/realtime'

const summary = ref<DashboardSummary | null>(null)
const liveDevices = ref<RealtimeDevice[]>([])
const tempTrend = ref<{ ts: string; value: string }[]>([])

let timer: any
let chartTemp: echarts.ECharts | null = null
let chartStatus: echarts.ECharts | null = null
let chartProduct: echarts.ECharts | null = null

const chartTempEl = ref<HTMLDivElement>()
const chartStatusEl = ref<HTMLDivElement>()
const chartProductEl = ref<HTMLDivElement>()

const now = ref(new Date().toLocaleString('zh-CN'))

async function load() {
  const [sumRes, liveRes]: any[] = await Promise.all([getDashboardSummary(), getRealtime()])
  summary.value = sumRes.data
  liveDevices.value = liveRes.data
  now.value = new Date().toLocaleString('zh-CN')

  // 拉一条温度趋势(任意第一台在线设备)
  const liveDev = liveDevices.value.find(d => d.status === 1)
  if (liveDev) {
    const prop = liveDev.properties.find(p => p.identifier === 'temperature')
    if (prop) {
      try {
        const end = Date.now()
        const start = end - 3600_000
        const hRes: any = await getHistory(liveDev.deviceId, 'temperature', 'double', start, end)
        tempTrend.value = hRes.data ?? []
      } catch (e) {
        tempTrend.value = []
      }
    }
  }
  await nextTick()
  drawCharts()
}

function drawCharts() {
  if (chartTempEl.value && !chartTemp) {
    chartTemp = echarts.init(chartTempEl.value, 'dark')
  }
  if (chartStatusEl.value && !chartStatus) {
    chartStatus = echarts.init(chartStatusEl.value, 'dark')
  }
  if (chartProductEl.value && !chartProduct) {
    chartProduct = echarts.init(chartProductEl.value, 'dark')
  }

  // 温度趋势
  const tempData = tempTrend.value
    .map(p => [Number(p.ts), Number(p.value)])
    .filter(([_, v]) => !isNaN(v))
    .sort((a, b) => a[0] - b[0])

  chartTemp?.setOption({
    grid: { left: 50, right: 20, top: 20, bottom: 30 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'time',
      axisLabel: { color: '#9bc4e0', fontSize: 10 },
      axisLine: { lineStyle: { color: '#1e4f6f' } }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#9bc4e0', fontSize: 10 },
      splitLine: { lineStyle: { color: 'rgba(155,196,224,0.1)' } }
    },
    series: [{
      type: 'line',
      smooth: true,
      symbol: 'none',
      sampling: 'lttb',
      itemStyle: { color: '#00d4ff' },
      lineStyle: { width: 2, color: '#00d4ff', shadowColor: '#00d4ff', shadowBlur: 8 },
      areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
        { offset: 0, color: 'rgba(0,212,255,0.5)' },
        { offset: 1, color: 'rgba(0,212,255,0)' }
      ]) },
      data: tempData
    }]
  })

  // 设备状态饼图
  const s = summary.value
  if (s) {
    chartStatus?.setOption({
      tooltip: { trigger: 'item' },
      legend: {
        bottom: 0,
        textStyle: { color: '#9bc4e0', fontSize: 11 }
      },
      series: [{
        type: 'pie',
        radius: ['45%', '70%'],
        avoidLabelOverlap: false,
        itemStyle: { borderRadius: 4, borderColor: '#031a30', borderWidth: 2 },
        label: { color: '#fff', fontSize: 12, formatter: '{b}\n{d}%' },
        data: [
          { value: s.deviceByStatus.online, name: '在线', itemStyle: { color: '#67c23a' } },
          { value: s.deviceByStatus.offline, name: '离线', itemStyle: { color: '#909399' } },
          { value: s.deviceByStatus.disabled, name: '禁用', itemStyle: { color: '#f56c6c' } }
        ]
      }]
    })

    // 产品分布柱图
    chartProduct?.setOption({
      grid: { left: 80, right: 20, top: 20, bottom: 30 },
      tooltip: { trigger: 'axis' },
      xAxis: {
        type: 'category',
        data: s.productDistribution.map(p => p.productName || p.productKey),
        axisLabel: { color: '#9bc4e0', fontSize: 11, interval: 0, rotate: 20 }
      },
      yAxis: {
        type: 'value',
        axisLabel: { color: '#9bc4e0' },
        splitLine: { lineStyle: { color: 'rgba(155,196,224,0.1)' } }
      },
      series: [{
        type: 'bar',
        data: s.productDistribution.map(p => p.count),
        barWidth: '40%',
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#00d4ff' },
            { offset: 1, color: '#0a4a7a' }
          ])
        }
      }]
    })
  }
}

function onResize() {
  chartTemp?.resize()
  chartStatus?.resize()
  chartProduct?.resize()
}

watch(summary, drawCharts)

onMounted(() => {
  load()
  timer = setInterval(load, 10000)
  window.addEventListener('resize', onResize)
})
onBeforeUnmount(() => {
  clearInterval(timer)
  window.removeEventListener('resize', onResize)
  chartTemp?.dispose()
  chartStatus?.dispose()
  chartProduct?.dispose()
})
</script>

<template>
  <div class="screen">
    <!-- 顶部标题栏 -->
    <div class="screen-header">
      <h1 class="screen-title">物联网平台 · 可视化大屏</h1>
      <div class="screen-time">{{ now }}</div>
    </div>

    <!-- 4 个核心数字 -->
    <div class="stat-row">
      <div class="big-card">
        <div class="label">设备总数</div>
        <div class="value">{{ summary?.deviceTotal ?? 0 }}</div>
        <div class="suffix">台</div>
      </div>
      <div class="big-card online">
        <div class="label">在线设备</div>
        <div class="value">{{ summary?.deviceByStatus.online ?? 0 }}</div>
        <div class="suffix">台</div>
      </div>
      <div class="big-card warn">
        <div class="label">待处理告警</div>
        <div class="value">{{ summary?.pendingAlerts ?? 0 }}</div>
        <div class="suffix">条</div>
      </div>
      <div class="big-card cyan">
        <div class="label">产品数</div>
        <div class="value">{{ summary?.productTotal ?? 0 }}</div>
        <div class="suffix">个</div>
      </div>
    </div>

    <!-- 图表区: 3 列 -->
    <div class="grid">
      <!-- 温度趋势 -->
      <div class="grid-card tall">
        <div class="grid-title">设备温度趋势 (最近 1 小时)</div>
        <div ref="chartTempEl" class="chart"></div>
      </div>

      <!-- 设备状态分布 -->
      <div class="grid-card">
        <div class="grid-title">设备状态分布</div>
        <div ref="chartStatusEl" class="chart"></div>
      </div>

      <!-- 产品 / 设备 -->
      <div class="grid-card">
        <div class="grid-title">产品 / 设备分布</div>
        <div ref="chartProductEl" class="chart"></div>
      </div>
    </div>

    <!-- 底部最近告警 + 在线设备 -->
    <div class="grid">
      <div class="grid-card">
        <div class="grid-title">最近告警</div>
        <div class="alert-list">
          <div v-for="a in summary?.recentAlerts ?? []" :key="a.id" class="alert-item"
               :class="a.level.toLowerCase()">
            <span class="alert-time">{{ a.createdAt }}</span>
            <span class="alert-level">{{ a.level }}</span>
            <span class="alert-title">{{ a.title }}</span>
            <span class="alert-device">{{ a.deviceKey }}</span>
          </div>
          <div v-if="(summary?.recentAlerts ?? []).length === 0" class="empty-mini">
            一切正常
          </div>
        </div>
      </div>
      <div class="grid-card">
        <div class="grid-title">在线设备 ({{ liveDevices.filter(d => d.status === 1).length }})</div>
        <div class="device-list">
          <div v-for="d in liveDevices.filter(x => x.status === 1).slice(0, 8)" :key="d.deviceId" class="device-item">
            <span class="dot"></span>
            <span class="name">{{ d.deviceName }}</span>
            <span class="key">{{ d.deviceKey }}</span>
            <span class="product">{{ d.productName }}</span>
          </div>
          <div v-if="liveDevices.filter(d => d.status === 1).length === 0" class="empty-mini">暂无在线设备</div>
        </div>
      </div>
      <div class="grid-card">
        <div class="grid-title">影子属性热点 (Top 8 设备)</div>
        <div class="shadow-list">
          <div v-for="d in liveDevices.slice(0, 8)" :key="d.deviceId" class="shadow-item">
            <span class="dev">{{ d.deviceName }}</span>
            <span class="vals">
              <span v-for="p in d.properties.filter(x => x.value != null).slice(0, 3)" :key="p.identifier" class="v">
                {{ p.identifier }}={{ p.value }}
              </span>
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.screen {
  min-height: 100vh;
  background: linear-gradient(135deg, #031a30 0%, #062c4d 50%, #031a30 100%);
  color: #e6f1ff;
  padding: 20px;
}

.screen-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  margin-bottom: 24px;
  position: relative;
  &::before, &::after {
    content: '';
    position: absolute;
    top: 50%;
    width: 200px;
    height: 1px;
    background: linear-gradient(90deg, transparent, #00d4ff, transparent);
  }
  &::before { left: 0; }
  &::after { right: 0; }
}
.screen-title {
  font-size: 32px;
  font-weight: 700;
  background: linear-gradient(90deg, #00d4ff, #67e8f9, #00d4ff);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  letter-spacing: 2px;
  margin: 0;
  text-shadow: 0 0 20px rgba(0,212,255,0.3);
}
.screen-time { color: #9bc4e0; font-family: 'Menlo', monospace; font-size: 16px; }

.stat-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 16px; }
.big-card {
  background: linear-gradient(135deg, rgba(0,212,255,0.1) 0%, rgba(10,74,122,0.3) 100%);
  border: 1px solid rgba(0,212,255,0.3);
  border-radius: 8px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
  overflow: hidden;
  &::before {
    content: '';
    position: absolute;
    top: 0; left: 0; right: 0;
    height: 2px;
    background: linear-gradient(90deg, transparent, #00d4ff, transparent);
  }
  &.online { border-color: rgba(103,194,58,0.3); background: linear-gradient(135deg, rgba(103,194,58,0.1), rgba(40,80,30,0.3));
    &::before { background: linear-gradient(90deg, transparent, #67c23a, transparent); } }
  &.warn { border-color: rgba(230,162,60,0.3); background: linear-gradient(135deg, rgba(230,162,60,0.1), rgba(80,60,30,0.3));
    &::before { background: linear-gradient(90deg, transparent, #e6a23c, transparent); } }
  &.cyan { border-color: rgba(6,182,212,0.3); background: linear-gradient(135deg, rgba(6,182,212,0.1), rgba(10,80,100,0.3));
    &::before { background: linear-gradient(90deg, transparent, #06b6d4, transparent); } }
  .label { color: #9bc4e0; font-size: 13px; }
  .value { font-size: 48px; font-weight: 700; color: #00d4ff; font-family: 'Menlo', monospace; line-height: 1.2;
    text-shadow: 0 0 20px rgba(0,212,255,0.5); }
  &.online .value { color: #67c23a; text-shadow: 0 0 20px rgba(103,194,58,0.5); }
  &.warn .value { color: #e6a23c; text-shadow: 0 0 20px rgba(230,162,60,0.5); }
  &.cyan .value { color: #06b6d4; text-shadow: 0 0 20px rgba(6,182,212,0.5); }
  .suffix { color: #9bc4e0; font-size: 12px; }
}

.grid {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
  @media (max-width: 1100px) { grid-template-columns: 1fr; }
}
.grid-card {
  background: rgba(10,40,70,0.4);
  border: 1px solid rgba(0,212,255,0.2);
  border-radius: 8px;
  padding: 16px;
  position: relative;
  &::before {
    content: '';
    position: absolute;
    top: 0; left: 16px; right: 16px;
    height: 1px;
    background: linear-gradient(90deg, transparent, #00d4ff, transparent);
  }
  &.tall { grid-row: span 1; }
}
.grid-title {
  color: #00d4ff;
  font-size: 14px;
  margin-bottom: 12px;
  letter-spacing: 1px;
  &::before { content: '◆ '; color: #00d4ff; }
}
.chart { width: 100%; height: 280px; }

.alert-list, .device-list, .shadow-list { display: flex; flex-direction: column; gap: 6px; max-height: 280px; overflow-y: auto; }
.alert-item {
  display: grid;
  grid-template-columns: 150px 60px 1fr 80px;
  gap: 8px;
  padding: 6px 8px;
  border-left: 2px solid;
  background: rgba(255,255,255,0.02);
  font-size: 12px;
  border-radius: 2px;
  &.info { border-color: #909399; }
  &.warn { border-color: #e6a23c; }
  &.error { border-color: #f56c6c; }
  &.critical { border-color: #f56c6c; box-shadow: 0 0 8px rgba(245,108,108,0.3); }
  .alert-time { color: #6088a0; font-family: 'Menlo', monospace; font-size: 11px; }
  .alert-level { font-weight: 600; font-size: 11px; }
  &.info .alert-level { color: #909399; }
  &.warn .alert-level { color: #e6a23c; }
  &.error .alert-level, &.critical .alert-level { color: #f56c6c; }
  .alert-title { color: #e6f1ff; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .alert-device { color: #9bc4e0; font-family: monospace; font-size: 11px; }
}

.device-item {
  display: grid;
  grid-template-columns: 12px 100px 80px 1fr;
  gap: 8px;
  padding: 6px 0;
  font-size: 12px;
  align-items: center;
  .dot {
    width: 8px; height: 8px;
    background: #67c23a; border-radius: 50%;
    box-shadow: 0 0 6px #67c23a;
  }
  .name { color: #e6f1ff; font-weight: 500; }
  .key { color: #9bc4e0; font-family: monospace; font-size: 11px; }
  .product { color: #6088a0; }
}

.shadow-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 6px 0;
  font-size: 12px;
  border-bottom: 1px dashed rgba(155,196,224,0.1);
  &:last-child { border: none; }
  .dev { color: #e6f1ff; font-weight: 500; }
  .vals { display: flex; gap: 6px; flex-wrap: wrap; }
  .v {
    background: rgba(0,212,255,0.1);
    color: #00d4ff;
    padding: 2px 6px;
    border-radius: 2px;
    font-family: 'Menlo', monospace;
    font-size: 11px;
  }
}
.empty-mini { text-align: center; color: #6088a0; padding: 24px 0; }
</style>