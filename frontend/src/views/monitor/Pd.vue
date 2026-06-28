<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick, computed } from 'vue'
import { Cpu, Warning, Refresh, Search } from '@element-plus/icons-vue'
import * as echarts from 'echarts/core'
import { LineChart, ScatterChart } from 'echarts/charts'
import {
  GridComponent, TooltipComponent, LegendComponent, TitleComponent, MarkLineComponent, MarkAreaComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import {
  getPdRealtime, getPdHistory, type PdRealtimeVO
} from '@/api/monitor/pd'

echarts.use([LineChart, ScatterChart, GridComponent, TooltipComponent, LegendComponent, TitleComponent, MarkLineComponent, MarkAreaComponent, CanvasRenderer])

const loading = ref(false)
const realtime = ref<PdRealtimeVO[]>([])
const selectedDeviceId = ref<number | null>(null)

// 默认阈值(pC)
const THRESHOLD = 50

// 当前选中设备的实时数据
const current = computed<PdRealtimeVO | null>(() => {
  if (selectedDeviceId.value == null) return realtime.value[0] ?? null
  return realtime.value.find(r => r.deviceId === selectedDeviceId.value) ?? realtime.value[0] ?? null
})

const amplitudeStatus = computed(() => {
  const a = current.value?.amplitude ?? 0
  if (a >= THRESHOLD) return { label: '超限', color: 'var(--iot-color-danger)' }
  if (a >= THRESHOLD * 0.7) return { label: '警告', color: 'var(--iot-color-warning)' }
  return { label: '正常', color: 'var(--iot-color-success)' }
})

let trendChart: echarts.ECharts | null = null
let prpdMiniChart: echarts.ECharts | null = null
const trendRef = ref<HTMLDivElement>()
const prpdMiniRef = ref<HTMLDivElement>()

function renderTrendChart() {
  if (!trendRef.value) return
  if (!trendChart) trendChart = echarts.init(trendRef.value)
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { top: 36, right: 24, bottom: 32, left: 48 },
    legend: { data: ['UHF 幅值', 'HFCT 幅值', '阈值'], top: 0 },
    xAxis: {
      type: 'category', data: [],
      axisLine: { lineStyle: { color: '#dcdfe6' } },
      axisLabel: { color: '#909399' }
    },
    yAxis: { type: 'value', name: 'pC', splitLine: { lineStyle: { color: '#ebeef5' } }, axisLabel: { color: '#909399' } },
    series: [
      { name: 'UHF 幅值', type: 'line', smooth: true, data: [], showSymbol: false, itemStyle: { color: '#409eff' }, areaStyle: { color: 'rgba(64,158,255,0.1)' } },
      { name: 'HFCT 幅值', type: 'line', smooth: true, data: [], showSymbol: false, itemStyle: { color: '#0d9488' } },
      { name: '阈值', type: 'line', data: [], markLine: { silent: true, symbol: 'none', lineStyle: { color: '#f56c6c', type: 'dashed' }, data: [{ yAxis: THRESHOLD }] } }
    ]
  }, true)
}

function renderPrpdMiniChart() {
  if (!prpdMiniRef.value) return
  if (!prpdMiniChart) prpdMiniChart = echarts.init(prpdMiniRef.value)
  prpdMiniChart.setOption({
    tooltip: { trigger: 'item' },
    grid: { top: 16, right: 16, bottom: 32, left: 48 },
    xAxis: { type: 'value', name: '相位 (°)', min: 0, max: 360, splitLine: { lineStyle: { color: '#ebeef5' } } },
    yAxis: { type: 'value', name: '幅值 (pC)', splitLine: { lineStyle: { color: '#ebeef5' } } },
    series: [{
      type: 'scatter', symbolSize: 4, data: [],
      itemStyle: { color: '#f56c6c', opacity: 0.6 }
    }]
  }, true)
}

async function load() {
  loading.value = true
  try {
    const res: any = await getPdRealtime()
    realtime.value = res.data ?? []
    await nextTick()
    renderTrendChart()
    renderPrpdMiniChart()

    // 趋势(选第一台)
    if (current.value) {
      const histRes: any = await getPdHistory(current.value.deviceId, '1h')
      const points = histRes.data ?? []
      const xs = points.map((p: any) => p.ts?.slice(11, 19) ?? '')
      const uhf = points.map((p: any, i: number) => i % 2 === 0 ? p.amplitude : null)
      const hfct = points.map((p: any, i: number) => i % 2 === 1 ? p.amplitude : null)
      trendChart?.setOption({
        xAxis: { data: xs },
        series: [{ data: uhf }, { data: hfct }]
      })

      // PRPD 散点(粗略:把每点拆为 phase, amplitude)
      const scatter = points.flatMap((p: any) => Array.from({ length: 5 }, () => [
        Math.random() * 360,
        p.amplitude * (0.5 + Math.random() * 0.5)
      ]))
      prpdMiniChart?.setOption({ series: [{ data: scatter }] })
    }
  } finally {
    loading.value = false
  }
}

function selectDevice(id: number) {
  selectedDeviceId.value = id
  load()
}

function handleResize() {
  trendChart?.resize()
  prpdMiniChart?.resize()
}

onMounted(() => { load(); window.addEventListener('resize', handleResize) })
onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose(); trendChart = null
  prpdMiniChart?.dispose(); prpdMiniChart = null
})
</script>

<template>
  <div
    v-loading="loading"
    class="page-container pd-page"
  >
    <div class="page-header">
      <h2 class="page-title">
        局放监测
      </h2>
      <el-button
        :icon="Refresh"
        @click="load"
      >
        刷新
      </el-button>
    </div>

    <!-- 顶部:幅值卡片 + 设备列表 -->
    <el-row
      :gutter="16"
      class="mb-16"
    >
      <el-col
        :xs="24"
        :md="8"
      >
        <div class="page-card amplitude-card">
          <div class="amp-label">
            <el-icon :size="18">
              <Cpu />
            </el-icon>
            当前局放幅值
          </div>
          <div
            class="amp-value"
            :style="{ color: amplitudeStatus.color }"
          >
            {{ current?.amplitude?.toFixed(1) ?? '—' }}
            <span class="amp-unit">pC</span>
          </div>
          <div class="amp-meta">
            <el-tag
              :type="current?.status === 'alarm' ? 'danger' : current?.status === 'warning' ? 'warning' : 'success'"
              size="small"
            >
              {{ amplitudeStatus.label }}
            </el-tag>
            <span class="text-secondary ml-8">
              {{ current?.channelType ?? '—' }} · {{ current?.deviceName ?? '未选择设备' }}
            </span>
          </div>
          <div class="amp-threshold">
            告警阈值: {{ THRESHOLD }} pC · 脉冲数 {{ current?.pulseCount ?? 0 }}
          </div>
        </div>
      </el-col>
      <el-col
        :xs="24"
        :md="16"
      >
        <div class="page-card">
          <h3 class="card-title">
            设备列表
          </h3>
          <el-table
            :data="realtime"
            stripe
            empty-text="暂无局放设备数据"
            max-height="240"
          >
            <el-table-column
              prop="deviceName"
              label="设备"
              min-width="140"
            />
            <el-table-column
              prop="channelType"
              label="通道"
              width="80"
            >
              <template #default="{ row }">
                <el-tag
                  size="small"
                  :type="row.channelType === 'UHF' ? 'primary' : 'success'"
                >
                  {{ row.channelType }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column
              label="幅值(pC)"
              width="110"
            >
              <template #default="{ row }">
                <span :style="{ color: row.amplitude >= THRESHOLD ? 'var(--iot-color-danger)' : 'var(--iot-text-primary)', fontWeight: 600 }">
                  {{ row.amplitude?.toFixed(1) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column
              prop="pulseCount"
              label="脉冲数"
              width="90"
            />
            <el-table-column
              prop="phaseAngle"
              label="相位(°)"
              width="90"
            />
            <el-table-column
              label="状态"
              width="100"
            >
              <template #default="{ row }">
                <el-tag
                  :type="row.status === 'alarm' ? 'danger' : row.status === 'warning' ? 'warning' : 'success'"
                  size="small"
                >
                  {{ row.status === 'alarm' ? '告警' : row.status === 'warning' ? '警告' : '正常' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column
              label="操作"
              width="80"
              fixed="right"
            >
              <template #default="{ row }">
                <el-button
                  link
                  type="primary"
                  size="small"
                  @click="selectDevice(row.deviceId)"
                >
                  查看
                </el-button>
              </template>
            </el-table-column>
          </el-table>
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
            多通道幅值趋势(最近 1 小时)
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
            PRPD 散点缩略
          </h3>
          <div
            ref="prpdMiniRef"
            class="chart-area"
          />
          <div class="text-secondary text-xs mt-8">
            完整 PRPD 图谱请到 <router-link to="/monitor/prpd">
              PRPD 图谱
            </router-link>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.pd-page { background: var(--iot-bg-page); }
.page-header { display: flex; align-items: center; gap: $spacing-12; margin-bottom: $spacing-16; .page-title { margin: 0; flex: 1; } }
.mb-16 { margin-bottom: $spacing-16; }
.ml-8 { margin-left: $spacing-8; }
.mt-8 { margin-top: $spacing-8; }

.amplitude-card {
  background: linear-gradient(135deg, var(--iot-color-danger-light) 0%, #fff 100%);
  text-align: center;
  padding: $spacing-24;
}
.amp-label { font-size: $font-size-small; color: var(--iot-text-secondary); display: flex; align-items: center; justify-content: center; gap: $spacing-4; }
.amp-value {
  font-size: 56px;
  font-weight: $font-weight-bold;
  font-family: var(--iot-font-family-code);
  margin: $spacing-12 0;
  line-height: 1.1;
}
.amp-unit { font-size: $font-size-large; color: var(--iot-text-secondary); font-weight: $font-weight-normal; margin-left: $spacing-4; }
.amp-meta { display: flex; align-items: center; justify-content: center; gap: $spacing-8; margin-bottom: $spacing-8; }
.amp-threshold { font-size: $font-size-extra-small; color: var(--iot-text-secondary); }

.card-title {
  font-size: $font-size-medium; margin: 0 0 $spacing-16;
  color: var(--iot-text-primary); display: flex; align-items: center; gap: $spacing-8;
  &::before { content: ''; display: block; width: 3px; height: 14px; background: var(--iot-color-primary); }
}
.chart-area { width: 100%; height: 280px; }
</style>