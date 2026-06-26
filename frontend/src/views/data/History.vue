<script setup lang="ts">
import { ref, reactive, onMounted, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh, DataLine } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { getRealtime, type RealtimeDevice } from '@/api/data/realtime'
import { getHistory, getHistoryStats } from '@/api/data/realtime'
import { allProducts } from '@/api/iot/product'

// ========== 查询条件 ==========
const query = reactive({
  productId: undefined as number | undefined,
  deviceId: undefined as number | undefined,
  identifier: '' as string,
  timeRange: '1h'
})
const products = ref<any[]>([])
const devices = ref<RealtimeDevice[]>([])
const props = ref<{ identifier: string; name: string; type: string; unit: string }[]>([])

const timeRanges = [
  { value: '15m', label: '最近 15 分钟' },
  { value: '1h',  label: '最近 1 小时' },
  { value: '6h',  label: '最近 6 小时' },
  { value: '24h', label: '最近 24 小时' },
  { value: '7d',  label: '最近 7 天' }
]
const rangeMs: Record<string, number> = {
  '15m': 15 * 60_000,
  '1h':  60 * 60_000,
  '6h':  6 * 60 * 60_000,
  '24h': 24 * 60 * 60_000,
  '7d':  7 * 24 * 60 * 60_000
}

const stats = ref<Record<string, string>>({})
const points = ref<{ ts: string; value: string }[]>([])

const chartEl = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null

// ========== 加载选项 ==========
async function loadOptions() {
  const [pRes, rRes]: any[] = await Promise.all([allProducts(), getRealtime()])
  products.value = pRes.data ?? []
  devices.value = rRes.data ?? []
}

// 当选产品/设备变化时,加载物模型属性
async function loadProps() {
  if (!query.deviceId) {
    props.value = []
    return
  }
  // 找设备,看其物模型(通过后端实时数据已经返回了属性)
  const dev = devices.value.find(d => d.deviceId === query.deviceId)
  if (dev) {
    props.value = dev.properties.map(p => ({
      identifier: p.identifier,
      name: p.name || p.identifier,
      type: p.type || 'string',
      unit: p.unit || ''
    }))
    if (props.value.length > 0 && !query.identifier) {
      query.identifier = props.value[0].identifier
    }
  }
}

watch(() => query.productId, () => {
  // 筛选设备
  query.deviceId = undefined
})
watch(() => query.deviceId, async () => {
  await loadProps()
  await doQuery()
})

// ========== 查询 ==========
const loading = ref(false)
async function doQuery() {
  if (!query.deviceId || !query.identifier) {
    ElMessage.warning('请选择设备和属性')
    return
  }
  const prop = props.value.find(p => p.identifier === query.identifier)
  if (!prop) return

  const end = Date.now()
  const start = end - rangeMs[query.timeRange]
  loading.value = true
  try {
    const [hRes, sRes]: any[] = await Promise.all([
      getHistory(query.deviceId, query.identifier, prop.type, start, end),
      getHistoryStats(query.deviceId, query.identifier, prop.type, start, end)
    ])
    points.value = hRes.data ?? []
    stats.value = sRes.data ?? {}
    await nextTick()
    drawChart(prop)
  } finally {
    loading.value = false
  }
}

function drawChart(prop: any) {
  if (!chartEl.value) return
  if (!chart) chart = echarts.init(chartEl.value)
  const data = points.value
    .map(p => [Number(p.ts), Number(p.value)])
    .filter(([_, v]) => !isNaN(v))
    .sort((a, b) => a[0] - b[0])

  chart.setOption({
    grid: { left: 60, right: 30, top: 30, bottom: 50 },
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        const p = params[0]
        const d = new Date(p.value[0])
        return `${d.toLocaleString('zh-CN')}<br/><b>${p.value[1]}</b> ${prop.unit || ''}`
      }
    },
    xAxis: {
      type: 'time',
      axisLabel: { color: '#606266' }
    },
    yAxis: {
      type: 'value',
      name: prop.unit || '',
      axisLabel: { color: '#606266' }
    },
    dataZoom: [
      { type: 'inside', start: 0, end: 100 },
      { type: 'slider', start: 0, end: 100, height: 20, bottom: 10 }
    ],
    series: [{
      name: prop.name,
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      sampling: 'lttb',
      itemStyle: { color: '#409eff' },
      lineStyle: { width: 2 },
      areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
        { offset: 0, color: 'rgba(64,158,255,0.4)' },
        { offset: 1, color: 'rgba(64,158,255,0.05)' }
      ]) },
      data
    }]
  })
}

onMounted(async () => {
  await loadOptions()
})

window.addEventListener('resize', () => chart?.resize())
</script>

<template>
  <div class="page-container" v-loading="loading">
    <h2 class="page-title">历史数据</h2>

    <div class="page-card search-bar">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="产品">
          <el-select v-model="query.productId" placeholder="全部" clearable style="width: 180px" @change="query.deviceId = undefined">
            <el-option v-for="p in products" :key="p.id"
              :label="`${p.productKey} - ${p.productName}`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="设备">
          <el-select v-model="query.deviceId" placeholder="选设备" style="width: 180px" filterable>
            <el-option
              v-for="d in devices.filter(x => !query.productId || x.productKey === products.find(p => p.id === query.productId)?.productKey)"
              :key="d.deviceId" :label="`${d.deviceKey} (${d.deviceName})`" :value="d.deviceId" />
          </el-select>
        </el-form-item>
        <el-form-item label="属性">
          <el-select v-model="query.identifier" placeholder="选属性" style="width: 180px" :disabled="!query.deviceId">
            <el-option v-for="p in props" :key="p.identifier"
              :label="`${p.name} (${p.identifier})`" :value="p.identifier" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间">
          <el-select v-model="query.timeRange" style="width: 160px">
            <el-option v-for="r in timeRanges" :key="r.value" :label="r.label" :value="r.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="doQuery">查询</el-button>
          <el-button :icon="Refresh" @click="loadOptions">刷新设备</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 统计卡片 -->
    <el-row v-if="stats.count" :gutter="12" class="mb-12">
      <el-col :xs="12" :sm="6">
        <div class="stat-card">
          <div class="stat-num">{{ stats.count || 0 }}</div>
          <div class="stat-label">数据点数</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="stat-card stat-blue">
          <div class="stat-num">{{ stats.avg || '—' }}</div>
          <div class="stat-label">平均值</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="stat-card stat-red">
          <div class="stat-num">{{ stats.max || '—' }}</div>
          <div class="stat-label">最大值</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="stat-card stat-green">
          <div class="stat-num">{{ stats.min || '—' }}</div>
          <div class="stat-label">最小值</div>
        </div>
      </el-col>
    </el-row>

    <div class="page-card">
      <div class="chart-header">
        <span><el-icon><DataLine /></el-icon> {{ query.identifier || '属性' }} 趋势</span>
        <span class="text-muted">共 {{ points.length }} 个数据点</span>
      </div>
      <div ref="chartEl" class="chart-container"></div>
      <el-empty v-if="points.length === 0 && !loading" description="该时间段暂无数据,请先通过 MQTT 模拟器上报数据" />
    </div>
  </div>
</template>

<style scoped lang="scss">
.search-bar { margin-bottom: 12px; padding: 16px; :deep(.el-form-item) { margin-bottom: 0; } }
.mb-12 { margin-bottom: 12px; }
.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-weight: 500;
  span { display: flex; align-items: center; gap: 4px; }
  .text-muted { color: #909399; font-size: 12px; font-weight: 400; }
}
.chart-container { width: 100%; height: 420px; }

.stat-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px 12px;
  text-align: center;
  box-shadow: 0 1px 4px rgba(0,0,0,0.04);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  margin-bottom: 12px;
}
.stat-num { font-size: 22px; font-weight: 600; color: #303133; font-family: 'Menlo', monospace; }
.stat-label { font-size: 12px; color: #909399; }
.stat-blue { background: linear-gradient(135deg, #ecf5ff 0%, #fff 100%); color: #409eff; }
.stat-red { background: linear-gradient(135deg, #fef0f0 0%, #fff 100%); color: #f56c6c; }
.stat-green { background: linear-gradient(135deg, #f0f9eb 0%, #fff 100%); color: #67c23a; }
</style>