<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, computed, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { Connection, Warning, Tickets, DataLine, Refresh, Location, Cpu } from '@element-plus/icons-vue'
import * as echarts from 'echarts/core'
import { PieChart, BarChart } from 'echarts/charts'
import {
  GridComponent, TooltipComponent, LegendComponent, TitleComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { getDashboardSummary, type DashboardSummary } from '@/api/dashboard'
import { getWorkOrderStats, type WorkOrderStatsVO } from '@/api/workorder'

echarts.use([PieChart, BarChart, GridComponent, TooltipComponent, LegendComponent, TitleComponent, CanvasRenderer])

const router = useRouter()
const loading = ref(false)
const data = ref<DashboardSummary | null>(null)
const workOrderStats = ref<WorkOrderStatsVO>({ pending: 0, processing: 0, completed: 0, overdue: 0 })
const currentTime = ref(new Date().toLocaleString('zh-CN'))

let pieChart: echarts.ECharts | null = null
const pieRef = ref<HTMLDivElement>()
let timer: any

const healthScore = computed(() => {
  if (!data.value || data.value.deviceTotal === 0) return 100
  // 健康度 = 在线率 * 100 - 待处理告警折减
  const onlineRate = data.value.deviceByStatus.online / data.value.deviceTotal
  const penalty = Math.min(0.3, (data.value.pendingAlerts ?? 0) * 0.02)
  return Math.max(0, Math.round(onlineRate * 100 - penalty * 100))
})

const healthColor = computed(() => {
  if (healthScore.value >= 90) return 'var(--iot-color-success)'
  if (healthScore.value >= 75) return 'var(--iot-color-primary)'
  if (healthScore.value >= 60) return 'var(--iot-color-warning)'
  return 'var(--iot-color-danger)'
})

const healthLabel = computed(() => {
  if (healthScore.value >= 90) return '优秀'
  if (healthScore.value >= 75) return '良好'
  if (healthScore.value >= 60) return '一般'
  return '异常'
})

const levelMap: Record<string, { label: string; color: string; tag: string }> = {
  INFO: { label: '信息', color: '#909399', tag: 'info' },
  NOTICE: { label: '注意', color: '#909399', tag: 'info' },
  WARN: { label: '警告', color: '#e6a23c', tag: 'warning' },
  ABNORMAL: { label: '异常', color: '#e6a23c', tag: 'warning' },
  ERROR: { label: '故障', color: '#f56c6c', tag: 'danger' },
  SERIOUS: { label: '严重', color: '#f78989', tag: 'danger' },
  CRITICAL: { label: '紧急', color: '#f56c6c', tag: 'danger' },
  URGENT: { label: '紧急', color: '#f56c6c', tag: 'danger' }
}

function renderPie() {
  if (!pieRef.value || !data.value) return
  if (!pieChart) pieChart = echarts.init(pieRef.value)
  const s = data.value.deviceByStatus
  pieChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { orient: 'vertical', right: 8, top: 'middle', textStyle: { color: '#606266', fontSize: 12 } },
    series: [{
      type: 'pie', radius: ['45%', '70%'], center: ['38%', '50%'],
      itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
      label: { show: false },
      data: [
        { value: s.online, name: '正常', itemStyle: { color: '#67c23a' } },
        { value: (data.value.pendingAlerts ?? 0), name: '预警', itemStyle: { color: '#e6a23c' } },
        { value: 0, name: '故障', itemStyle: { color: '#f56c6c' } },
        { value: s.offline + s.disabled, name: '离线', itemStyle: { color: '#909399' } }
      ]
    }]
  })
}

async function load() {
  loading.value = true
  try {
    const [dRes, wRes]: any[] = await Promise.all([
      getDashboardSummary(),
      getWorkOrderStats().catch(() => ({ data: { pending: 0, processing: 0, completed: 0, overdue: 0 } }))
    ])
    data.value = dRes.data
    workOrderStats.value = wRes.data ?? workOrderStats.value
    currentTime.value = new Date().toLocaleString('zh-CN')
    await nextTick()
    renderPie()
  } finally {
    loading.value = false
  }
}

function go(path: string) { router.push(path) }

function handleResize() { pieChart?.resize() }

onMounted(() => {
  load()
  timer = setInterval(load, 30000)
  window.addEventListener('resize', handleResize)
})
onBeforeUnmount(() => {
  clearInterval(timer)
  window.removeEventListener('resize', handleResize)
  pieChart?.dispose(); pieChart = null
})
</script>

<template>
  <div class="page-container dashboard" v-loading="loading">
    <div class="page-header">
      <h2 class="page-title">工作台</h2>
      <div class="time-display">{{ currentTime }}</div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <!-- 4 核心统计卡(蓝图:在线终端数 / 今日告警 / 未处理工单 / 健康度) -->
    <el-row :gutter="16" class="mb-16">
      <el-col :xs="12" :sm="6">
        <div class="stat-card stat-green clickable" @click="go('/device/list')">
          <el-icon :size="32"><Connection /></el-icon>
          <div class="stat-num">{{ data?.deviceByStatus.online ?? 0 }}</div>
          <div class="stat-label">在线终端数</div>
          <div class="stat-rate">
            在线率 {{ data?.deviceTotal ? Math.round((data.deviceByStatus.online / data.deviceTotal) * 100) : 0 }}%
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="stat-card stat-red clickable" @click="go('/alert/center')">
          <el-icon :size="32"><Warning /></el-icon>
          <div class="stat-num">{{ data?.todayAlerts ?? 0 }}</div>
          <div class="stat-label">今日告警</div>
          <div class="stat-rate text-secondary">待处理 {{ data?.pendingAlerts ?? 0 }}</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="stat-card stat-blue clickable" @click="go('/workorder/list')">
          <el-icon :size="32"><Tickets /></el-icon>
          <div class="stat-num">{{ workOrderStats.pending + workOrderStats.processing }}</div>
          <div class="stat-label">未处理工单</div>
          <div class="stat-rate text-secondary">
            待派 {{ workOrderStats.pending }} · 处理中 {{ workOrderStats.processing }}
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="stat-card" :style="{ background: `linear-gradient(135deg, ${healthColor}15 0%, #fff 100%)`, color: healthColor }">
          <el-icon :size="32"><DataLine /></el-icon>
          <div class="stat-num" :style="{ color: healthColor }">{{ healthScore }}</div>
          <div class="stat-label">健康度评分 · {{ healthLabel }}</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <!-- 左:实时告警滚动 -->
      <el-col :xs="24" :md="14">
        <div class="page-card">
          <div class="card-title-row">
            <h3 class="card-title">实时告警(最近 10 条)</h3>
            <el-link type="primary" :underline="false" @click="go('/alert/center')">查看全部 →</el-link>
          </div>
          <el-table :data="(data?.recentAlerts ?? []).slice(0, 10)" stripe empty-text="一切正常,暂无告警" max-height="380">
            <el-table-column label="级别" width="100">
              <template #default="{ row }">
                <el-tag :type="levelMap[row.level]?.tag as any" size="small">
                  {{ levelMap[row.level]?.label || row.level }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="title" label="内容" min-width="220" show-overflow-tooltip />
            <el-table-column label="设备" width="140">
              <template #default="{ row }">
                <el-tag size="small" type="info">{{ row.deviceKey }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="触发时间" width="170" />
          </el-table>
        </div>

        <div class="page-card mt-16">
          <h3 class="card-title">产品 / 设备分布(Top 10)</h3>
          <el-table :data="(data?.productDistribution ?? []).slice(0, 10)" stripe empty-text="暂无产品">
            <el-table-column prop="productKey" label="产品 Key" width="180">
              <template #default="{ row }">
                <el-tag size="small" type="info">{{ row.productKey }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="productName" label="产品名" min-width="180" />
            <el-table-column prop="count" label="设备数" width="120">
              <template #default="{ row }">
                <span class="font-semibold text-primary">{{ row.count }}</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>

      <!-- 右:饼图 + GIS 缩略 -->
      <el-col :xs="24" :md="10">
        <div class="page-card">
          <h3 class="card-title">设备状态分布</h3>
          <div ref="pieRef" class="chart-area"></div>
        </div>

        <div class="page-card mt-16">
          <h3 class="card-title">
            <el-icon><Location /></el-icon> GIS 缩略地图
          </h3>
          <div class="gis-mini">
            <el-icon :size="48" color="#c0c4cc"><Location /></el-icon>
            <p class="text-secondary text-sm">地图底图选型待 OQ-007 客户确认</p>
            <p class="text-secondary text-xs">接入后将标记最近告警点位,点击下钻到设备详情</p>
            <el-link type="primary" :underline="false" @click="go('/monitor/gis')">打开完整地图 →</el-link>
          </div>
        </div>

        <div class="page-card mt-16">
          <h3 class="card-title"><el-icon><Cpu /></el-icon> 平台能力</h3>
          <ul class="info-list">
            <li>✓ 局放 / PRPD / 温度 / 环境多维监测</li>
            <li>✓ 工单闭环(告警 → 派单 → 处理 → 反馈)</li>
            <li>✓ 多租户隔离 + RBAC 权限</li>
            <li>✓ MQTT / TCP 设备接入 + TDengine 时序存储</li>
            <li>✓ 知识库 / 报表 / 运维统计</li>
          </ul>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.dashboard { background: var(--iot-bg-page); }
.page-header { display: flex; align-items: center; gap: $spacing-12; margin-bottom: $spacing-16; .page-title { margin: 0; flex: 1; } }
.time-display { color: var(--iot-text-secondary); font-family: var(--iot-font-family-code); font-size: $font-size-small; }
.mb-16 { margin-bottom: $spacing-16; }
.mt-16 { margin-top: $spacing-16; }

.stat-card {
  background: var(--iot-bg-card); border-radius: $radius-large;
  padding: $spacing-20 $spacing-16; text-align: center;
  box-shadow: var(--iot-shadow-light);
  display: flex; flex-direction: column; align-items: center; gap: $spacing-4;
  margin-bottom: $spacing-12; transition: transform $transition-base, box-shadow $transition-base;
  &.clickable { cursor: pointer; &:hover { transform: translateY(-2px); box-shadow: var(--iot-shadow-md); } }
}
.stat-num { font-size: 32px; font-weight: $font-weight-semibold; color: var(--iot-text-primary); font-family: var(--iot-font-family-code); }
.stat-label { font-size: $font-size-extra-small; color: var(--iot-text-secondary); }
.stat-rate { font-size: $font-size-extra-small; color: var(--iot-color-success); margin-top: $spacing-2; }
.stat-green { color: var(--iot-color-success); background: linear-gradient(135deg, #f0f9eb 0%, #fff 100%); }
.stat-red { color: var(--iot-color-danger); background: linear-gradient(135deg, var(--iot-color-danger-light) 0%, #fff 100%); }
.stat-blue { color: var(--iot-color-primary); background: linear-gradient(135deg, var(--iot-color-primary-light-9) 0%, #fff 100%); }

.card-title {
  font-size: $font-size-medium; margin: 0; color: var(--iot-text-primary);
  display: flex; align-items: center; gap: $spacing-8;
  &::before { content: ''; display: block; width: 3px; height: 14px; background: var(--iot-color-primary); }
}
.card-title-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: $spacing-16; }

.chart-area { width: 100%; height: 240px; }

.gis-mini {
  height: 180px;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  background: linear-gradient(135deg, var(--iot-bg-page) 0%, var(--iot-bg-card) 100%);
  border-radius: $radius-base; padding: $spacing-16; text-align: center; gap: $spacing-4;
}

.info-list {
  font-size: $font-size-small; line-height: 2.2;
  color: var(--iot-text-regular); margin: 0; padding-left: $spacing-4;
}
</style>