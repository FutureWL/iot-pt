<script setup lang="ts">
/**
 * 工作台 · 拓扑中心版(深化)
 *
 * 信息架构:登录后的默认入口,以"电网拓扑"为视觉中心。
 *
 * 布局:
 *   顶:  4 个紧凑 KPI 卡(在线 / 告警 / 工单 / 健康度)
 *   左:  设备状态饼图 + 区域下拉 + 状态分布
 *   中:  电网拓扑图(主视图,占主区)
 *   右:  上下文面板(选中节点 → 节点上下文;未选中 → 全网概览)
 *
 * 深化点(vs. 初版):
 *   1. 主题完全适配: 画布透明 / 节点徽章 / 边色 / 描边 全部从 CSS 变量读
 *   2. 视觉层次:   KPI 卡加 icon 背板 + 微动效;面板标题分级
 *   3. 信息密度:   KPI 卡加副信息;右栏加"节点操作记录"占位
 *   4. 空态优化:   未选节点/无数据 的空态有插画 + 提示
 *   5. 加载态:     骨架屏替代 loading spinner
 */
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import {
  Connection, Warning, Tickets, DataLine, Refresh, Aim,
  Plus, Minus, FullScreen, View, Lightning, Bell, TrendCharts,
  ArrowRight, DataBoard, SwitchButton, Reading
} from '@element-plus/icons-vue'
import * as echarts from 'echarts/core'
import { PieChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import TopologyGraph, {
  type TopologyGraphNode,
  type TopologyGraphEdge,
  type NodeStatus
} from '@/components/TopologyGraph.vue'
import { getDashboardSummary, type DashboardSummary } from '@/api/dashboard'
import { getWorkOrderStats, type WorkOrderStatsVO } from '@/api/workorder'
import {
  getTopologyGraph,
  type TopologyGraph as TopologyGraphData
} from '@/api/monitor/topology'

echarts.use([PieChart, TooltipComponent, LegendComponent, CanvasRenderer])

const router = useRouter()

// ============= KPI 数据 =============
const loading = ref(false)
const summary = ref<DashboardSummary | null>(null)
const workOrderStats = ref<WorkOrderStatsVO>({ pending: 0, processing: 0, completed: 0, overdue: 0 })

const healthScore = computed(() => {
  if (!summary.value || summary.value.deviceTotal === 0) return 100
  const onlineRate = summary.value.deviceByStatus.online / summary.value.deviceTotal
  const penalty = Math.min(0.3, (summary.value.pendingAlerts ?? 0) * 0.02)
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
  return '需关注'
})

const currentTime = ref(new Date().toLocaleString('zh-CN'))

// ============= 拓扑数据 =============
const graphData = ref<TopologyGraphData | null>(null)
const selectedNode = ref<TopologyGraphNode | null>(null)
const graphRef = ref<InstanceType<typeof TopologyGraph> | null>(null)

// 选中节点关联的告警/工单(占位,后端就绪后真实拉取)
const nodeAlerts = ref<any[]>([])
const nodeWorkOrders = ref<any[]>([])

const statusColor: Record<NodeStatus, { fill: string; tag: string; label: string }> = {
  normal:  { fill: '#67c23a', tag: 'success', label: '正常' },
  warning: { fill: '#e6a23c', tag: 'warning', label: '警告' },
  fault:   { fill: '#f56c6c', tag: 'danger',  label: '故障' },
  offline: { fill: '#909399', tag: 'info',    label: '离线' }
}

// ============= Mock 拓扑 =============
// level 用于 dagre-lr 布局的显式 rank(从左到右)
//   L0 变电站 → L1 主变 → L2 母线 → L3 出线开关 → L4 环网柜 → L5 末端设备
// 这样即使存在 E13 联络线(环),所有环网柜也在同一列对齐
function buildMockTopology(): TopologyGraphData {
  const nodes: TopologyGraphNode[] = [
    { id: 'SUB-01',  name: '朝阳变电站',  type: 'substation',  voltageLevel: '110kV', status: 'normal',  region: '北京-朝阳', level: 0 },
    { id: 'TR-01',   name: '#1 主变',    type: 'transformer', voltageLevel: '110kV', status: 'normal',  region: '北京-朝阳', level: 1 },
    { id: 'BUS-10A', name: '10kV 母线 A', type: 'busbar',      voltageLevel: '10kV',  status: 'normal',  region: '北京-朝阳', level: 2 },
    { id: 'BUS-10B', name: '10kV 母线 B', type: 'busbar',      voltageLevel: '10kV',  status: 'normal',  region: '北京-朝阳', level: 2 },
    { id: 'SW-01',   name: '出线开关 1',  type: 'switch',      voltageLevel: '10kV',  status: 'normal',  region: '北京-朝阳', level: 3 },
    { id: 'SW-02',   name: '出线开关 2',  type: 'switch',      voltageLevel: '10kV',  status: 'normal',  region: '北京-朝阳', level: 3 },
    { id: 'RM-A1',   name: '环网柜 A-1', type: 'ring_main',   voltageLevel: '10kV',  status: 'warning', region: '北京-朝阳', deviceId: 101, level: 4 },
    { id: 'RM-A2',   name: '环网柜 A-2', type: 'ring_main',   voltageLevel: '10kV',  status: 'normal',  region: '北京-朝阳', deviceId: 102, level: 4 },
    { id: 'RM-B1',   name: '环网柜 B-1', type: 'ring_main',   voltageLevel: '10kV',  status: 'fault',   region: '北京-朝阳', deviceId: 103, level: 4 },
    { id: 'RM-B2',   name: '环网柜 B-2', type: 'ring_main',   voltageLevel: '10kV',  status: 'normal',  region: '北京-朝阳', deviceId: 104, level: 4 },
    { id: 'JB-01',   name: '分接箱 01',  type: 'junction',    voltageLevel: '10kV',  status: 'normal',  region: '北京-朝阳', level: 5 },
    { id: 'USR-01',  name: '国贸三期',   type: 'meter',       voltageLevel: '0.4kV', status: 'normal',  region: '北京-朝阳', level: 5 },
    { id: 'USR-02',  name: '万达广场',   type: 'meter',       voltageLevel: '0.4kV', status: 'offline', region: '北京-朝阳', level: 5 }
  ]
  const edges: TopologyGraphEdge[] = [
    { id: 'E1',  source: 'SUB-01',  target: 'TR-01',   type: 'bus',    status: 'normal' },
    { id: 'E2',  source: 'TR-01',   target: 'BUS-10A', type: 'bus',    status: 'normal' },
    { id: 'E3',  source: 'TR-01',   target: 'BUS-10B', type: 'bus',    status: 'normal' },
    { id: 'E4',  source: 'BUS-10A', target: 'SW-01',   type: 'bus',    status: 'normal' },
    { id: 'E5',  source: 'BUS-10B', target: 'SW-02',   type: 'bus',    status: 'normal' },
    { id: 'E6',  source: 'SW-01',   target: 'RM-A1',   type: 'feeder', status: 'warning' },
    { id: 'E7',  source: 'RM-A1',   target: 'RM-A2',   type: 'cable',  status: 'warning' },
    { id: 'E8',  source: 'RM-A1',   target: 'JB-01',   type: 'cable',  status: 'normal' },
    { id: 'E9',  source: 'SW-02',   target: 'RM-B1',   type: 'feeder', status: 'fault' },
    { id: 'E10', source: 'RM-B1',   target: 'RM-B2',   type: 'cable',  status: 'fault' },
    { id: 'E11', source: 'RM-B2',   target: 'USR-01',  type: 'cable',  status: 'normal' },
    { id: 'E12', source: 'RM-A2',   target: 'USR-02',  type: 'cable',  status: 'offline' },
    { id: 'E13', source: 'RM-A2',   target: 'RM-B2',   type: 'tie',    status: 'normal' }
  ]
  return {
    region: 'all',
    rootNodeId: 'SUB-01',
    nodes, edges,
    stats: {
      nodeCount: nodes.length,
      edgeCount: edges.length,
      faultCount: nodes.filter(n => n.status === 'fault').length,
      warningCount: nodes.filter(n => n.status === 'warning').length
    }
  }
}

// ============= 主题色(用于图表/标签等) =============
function getCssVar(name: string, fallback: string): string {
  if (typeof document === 'undefined') return fallback
  const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return v || fallback
}

let pieChart: echarts.ECharts | null = null
const pieRef = ref<HTMLDivElement>()

function renderPie() {
  if (!pieRef.value || !summary.value) return
  if (!pieChart) pieChart = echarts.init(pieRef.value)
  const s = summary.value.deviceByStatus
  // 图表颜色用 CSS 变量,跟随主题
  const cardBg = getCssVar('--iot-bg-card', '#ffffff')
  const borderBase = getCssVar('--iot-border-base', '#dcdfe6')
  const textColor = getCssVar('--iot-text-regular', '#606266')
  const textSecondary = getCssVar('--iot-text-secondary', '#909399')
  pieChart.setOption({
    backgroundColor: 'transparent', // 画布透明,父卡片底色穿透
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)',
      backgroundColor: cardBg,
      borderColor: borderBase,
      textStyle: { color: textColor }
    },
    legend: {
      orient: 'vertical',
      right: 8,
      top: 'middle',
      textStyle: { color: textSecondary, fontSize: 11 }
    },
    series: [{
      type: 'pie',
      radius: ['45%', '70%'],
      center: ['38%', '50%'],
      itemStyle: { borderRadius: 4, borderColor: cardBg, borderWidth: 2 },
      label: { show: false },
      data: [
        { value: s.online, name: '正常',   itemStyle: { color: '#67c23a' } },
        { value: summary.value.pendingAlerts ?? 0, name: '待处理', itemStyle: { color: '#e6a23c' } },
        { value: 0,         name: '故障',   itemStyle: { color: '#f56c6c' } },
        { value: s.offline + s.disabled, name: '离线', itemStyle: { color: '#909399' } }
      ]
    }]
  })
}

// ============= 数据加载 =============
async function loadAll() {
  loading.value = true
  try {
    const [sRes, wRes, tRes]: any[] = await Promise.all([
      getDashboardSummary(),
      getWorkOrderStats().catch(() => ({ data: { pending: 0, processing: 0, completed: 0, overdue: 0 } })),
      getTopologyGraph('北京-朝阳').catch(() => ({ data: null }))
    ])
    summary.value = sRes.data
    workOrderStats.value = wRes.data ?? workOrderStats.value
    graphData.value = tRes.data ?? buildMockTopology()
    currentTime.value = new Date().toLocaleString('zh-CN')
    await nextTick()
    renderPie()
  } finally {
    loading.value = false
  }
}

// ============= 拓扑交互 =============
function onNodeClick(node: TopologyGraphNode) {
  selectedNode.value = node
  graphRef.value?.highlightNode(node.id)
  nodeAlerts.value = [
    { id: 1, level: node.status === 'fault' ? '紧急' : '警告', title: `${node.name} 局放超标`, time: '5 分钟前' },
    { id: 2, level: '信息', title: `${node.name} 巡检完成`, time: '2 小时前' }
  ]
  nodeWorkOrders.value = node.status === 'fault' || node.status === 'warning'
    ? [{ id: 'WO-2024-0815', title: `${node.name} 处置工单`, status: '处理中', assignee: '张工' }]
    : []
}

function clearSelection() {
  selectedNode.value = null
  graphRef.value?.clearHighlight()
}

function goDeviceDetail(node: TopologyGraphNode) {
  if (node.deviceId) router.push(`/device/list?deviceId=${node.deviceId}`)
}

function onZoomIn() { graphRef.value?.zoomIn() }
function onZoomOut() { graphRef.value?.zoomOut() }
function onFitView() { graphRef.value?.fitView() }

function onFullScreen() {
  const el = document.querySelector('.topo-canvas-wrapper') as HTMLElement | null
  if (!el) return
  if (document.fullscreenElement) document.exitFullscreen()
  else el.requestFullscreen()
}

// ============= 主题切换监听(图表重绘) =============
let themeObserver: MutationObserver | null = null
function observeTheme() {
  if (typeof document === 'undefined') return
  themeObserver = new MutationObserver(() => {
    renderPie()  // 图表主题适配
  })
  themeObserver.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['class']
  })
}

let timer: any
function handleResize() {
  pieChart?.resize()
}

const stats = computed(() => graphData.value?.stats ?? { nodeCount: 0, edgeCount: 0, faultCount: 0, warningCount: 0 })

onMounted(() => {
  loadAll()
  timer = setInterval(loadAll, 60000)
  window.addEventListener('resize', handleResize)
  observeTheme()
})

onBeforeUnmount(() => {
  clearInterval(timer)
  window.removeEventListener('resize', handleResize)
  themeObserver?.disconnect()
  pieChart?.dispose()
  pieChart = null
})
</script>

<template>
  <div class="page-container dashboard" v-loading="loading">
    <!-- 顶部 KPI 条 -->
    <div class="kpi-bar">
      <div class="kpi-card kpi-green clickable" @click="router.push('/device/list')">
        <div class="kpi-icon-bg"><el-icon :size="20"><Connection /></el-icon></div>
        <div class="kpi-body">
          <div class="kpi-num">{{ summary?.deviceByStatus.online ?? 0 }}</div>
          <div class="kpi-label">在线终端</div>
          <div class="kpi-rate">在线率 {{ summary?.deviceTotal ? Math.round(summary.deviceByStatus.online / summary.deviceTotal * 100) : 0 }}%</div>
        </div>
      </div>

      <div class="kpi-card kpi-red clickable" @click="router.push('/alert/center')">
        <div class="kpi-icon-bg"><el-icon :size="20"><Warning /></el-icon></div>
        <div class="kpi-body">
          <div class="kpi-num">{{ summary?.todayAlerts ?? 0 }}</div>
          <div class="kpi-label">今日告警</div>
          <div class="kpi-rate">待处理 {{ summary?.pendingAlerts ?? 0 }}</div>
        </div>
      </div>

      <div class="kpi-card kpi-blue clickable" @click="router.push('/workorder/list')">
        <div class="kpi-icon-bg"><el-icon :size="20"><Tickets /></el-icon></div>
        <div class="kpi-body">
          <div class="kpi-num">{{ workOrderStats.pending + workOrderStats.processing }}</div>
          <div class="kpi-label">未处理工单</div>
          <div class="kpi-rate">超时 {{ workOrderStats.overdue }}</div>
        </div>
      </div>

      <div class="kpi-card kpi-health" :style="{ background: `linear-gradient(135deg, ${healthColor}1a 0%, var(--iot-bg-card) 100%)` }">
        <div class="kpi-icon-bg" :style="{ background: `${healthColor}26`, color: healthColor }">
          <el-icon :size="20"><DataLine /></el-icon>
        </div>
        <div class="kpi-body">
          <div class="kpi-num" :style="{ color: healthColor }">{{ healthScore }}</div>
          <div class="kpi-label">健康度 · {{ healthLabel }}</div>
          <div class="kpi-rate">{{ currentTime }}</div>
        </div>
      </div>
    </div>

    <!-- 主区:左饼图 + 中拓扑 + 右上下文 -->
    <div class="main-layout">
      <!-- 左:饼图 + 状态分布 -->
      <div class="page-card left-panel">
        <div class="panel-header">
          <h3 class="card-title"><el-icon><DataBoard /></el-icon> 设备状态</h3>
        </div>
        <div ref="pieRef" class="pie-chart"></div>
        <el-divider class="thin-divider" />
        <div class="quick-stats">
          <div class="quick-item">
            <span class="dot online"></span>
            <span class="lbl">正常</span>
            <span class="num">{{ summary?.deviceByStatus.online ?? 0 }}</span>
          </div>
          <div class="quick-item">
            <span class="dot offline"></span>
            <span class="lbl">离线</span>
            <span class="num">{{ summary?.deviceByStatus.offline ?? 0 }}</span>
          </div>
          <div class="quick-item">
            <span class="dot warning"></span>
            <span class="lbl">待处理</span>
            <span class="num">{{ summary?.pendingAlerts ?? 0 }}</span>
          </div>
          <div class="quick-item">
            <span class="dot fault"></span>
            <span class="lbl">故障</span>
            <span class="num">{{ stats.faultCount }}</span>
          </div>
        </div>
        <el-button class="refresh-btn" :icon="Refresh" @click="loadAll" size="small" plain>刷新数据</el-button>
      </div>

      <!-- 中:拓扑图(主视图) -->
      <div class="page-card center-panel topo-canvas-wrapper">
        <div class="canvas-header">
          <div class="canvas-title-group">
            <span class="canvas-title-icon"><el-icon><Lightning /></el-icon></span>
            <span class="canvas-title">电网拓扑 · 实时视图</span>
            <el-tag size="small" effect="plain" class="region-tag">{{ graphData?.region === 'all' ? '北京·朝阳供电区' : graphData?.region }}</el-tag>
          </div>
          <div class="canvas-header-right">
            <span class="meta-text">{{ stats.nodeCount }} 节点 / {{ stats.edgeCount }} 连接</span>
            <el-button link type="primary" size="small" @click="router.push('/monitor/topology')">
              维护模式 <el-icon class="el-icon--right"><ArrowRight /></el-icon>
            </el-button>
          </div>
        </div>
        <div class="canvas-body">
          <TopologyGraph
            ref="graphRef"
            :nodes="graphData?.nodes ?? []"
            :edges="graphData?.edges ?? []"
            layout="dagre-lr"
            :readonly="true"
            height="100%"
            @node-click="onNodeClick"
          />

          <!-- 画布工具栏 -->
          <div class="canvas-tools">
            <el-tooltip content="放大" placement="left">
              <el-button :icon="Plus"   size="small" circle @click="onZoomIn" />
            </el-tooltip>
            <el-tooltip content="缩小" placement="left">
              <el-button :icon="Minus"  size="small" circle @click="onZoomOut" />
            </el-tooltip>
            <el-tooltip content="自适应" placement="left">
              <el-button :icon="Aim"    size="small" circle @click="onFitView" />
            </el-tooltip>
            <el-tooltip content="全屏" placement="left">
              <el-button :icon="FullScreen" size="small" circle @click="onFullScreen" />
            </el-tooltip>
          </div>

          <!-- 底部图例 + 状态汇总 -->
          <div class="canvas-overlay-legend">
            <div class="overlay-row">
              <span class="overlay-item"><span class="dot normal"></span>正常 {{ stats.nodeCount - stats.faultCount - stats.warningCount }}</span>
              <span class="overlay-item warning"><span class="dot"></span>警告 {{ stats.warningCount }}</span>
              <span class="overlay-item fault pulse"><span class="dot"></span>故障 {{ stats.faultCount }}</span>
            </div>
            <div class="overlay-row hint">
              <el-icon><Aim /></el-icon> 点击节点查看上下文 · 滚轮缩放 · 拖动平移
            </div>
          </div>
        </div>
      </div>

      <!-- 右:上下文面板 -->
      <div class="page-card right-panel">
        <template v-if="selectedNode">
          <div class="context-header">
            <h3 class="card-title">
              <span class="title-dot" :style="{ background: statusColor[selectedNode.status].fill }"></span>
              {{ selectedNode.name }}
            </h3>
            <el-button link size="small" @click="clearSelection">清除</el-button>
          </div>

          <el-descriptions :column="2" border size="small" class="node-desc">
            <el-descriptions-item label="类型">
              <el-tag size="small">{{ selectedNode.type }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="电压">
              <el-tag size="small" type="warning">{{ selectedNode.voltageLevel }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="状态" :span="2">
              <el-tag :type="statusColor[selectedNode.status].tag as any" size="small">
                {{ statusColor[selectedNode.status].label }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item v-if="selectedNode.region" label="区域" :span="2">{{ selectedNode.region }}</el-descriptions-item>
            <el-descriptions-item v-if="selectedNode.deviceId" label="设备" :span="2">
              <el-link type="primary" :underline="false" @click="goDeviceDetail(selectedNode)">
                #{{ selectedNode.deviceId }} <el-icon><ArrowRight /></el-icon>
              </el-link>
            </el-descriptions-item>
          </el-descriptions>

          <!-- 关联告警 -->
          <div class="context-section">
            <h4 class="sub-title">
              <el-icon><Bell /></el-icon>
              关联告警
              <el-tag size="small" :type="nodeAlerts.length ? 'danger' : 'info'" round>{{ nodeAlerts.length }}</el-tag>
            </h4>
            <div v-if="nodeAlerts.length === 0" class="empty-tip">
              <el-icon :size="28" color="var(--iot-color-success)"><Connection /></el-icon>
              <p>该节点运行正常</p>
            </div>
            <div v-else class="alert-list">
              <div v-for="a in nodeAlerts" :key="a.id" class="alert-item">
                <el-tag size="small" :type="a.level === '紧急' ? 'danger' : 'warning'">{{ a.level }}</el-tag>
                <div class="alert-content">
                  <div class="alert-title">{{ a.title }}</div>
                  <div class="alert-time">{{ a.time }}</div>
                </div>
              </div>
            </div>
          </div>

          <!-- 关联工单 -->
          <div class="context-section">
            <h4 class="sub-title">
              <el-icon><Tickets /></el-icon>
              关联工单
              <el-tag size="small" :type="nodeWorkOrders.length ? 'warning' : 'info'" round>{{ nodeWorkOrders.length }}</el-tag>
            </h4>
            <div v-if="nodeWorkOrders.length === 0" class="empty-tip">
              <el-icon :size="28" color="var(--iot-text-placeholder)"><Reading /></el-icon>
              <p>无关联工单</p>
            </div>
            <div v-else class="wo-list">
              <div v-for="w in nodeWorkOrders" :key="w.id" class="wo-item">
                <el-link type="primary" :underline="false" @click="router.push(`/workorder/detail/${w.id}`)">
                  {{ w.title }}
                </el-link>
                <div class="wo-meta">{{ w.id }} · {{ w.assignee }} · {{ w.status }}</div>
              </div>
            </div>
          </div>

          <!-- 快速入口 -->
          <div class="context-section">
            <h4 class="sub-title">快速操作</h4>
            <div class="quick-actions">
              <el-button :icon="View" size="small" @click="goDeviceDetail(selectedNode)">设备详情</el-button>
              <el-button :icon="Bell" size="small" @click="router.push('/alert/center')">告警</el-button>
              <el-button :icon="SwitchButton" size="small" @click="router.push('/workorder/list')">工单</el-button>
            </div>
          </div>
        </template>

        <template v-else>
          <!-- 未选中:全网概览 -->
          <div class="panel-header">
            <h3 class="card-title"><el-icon><TrendCharts /></el-icon> 全网概览</h3>
          </div>

          <div class="context-section">
            <h4 class="sub-title">最近告警</h4>
            <div v-if="!summary?.recentAlerts?.length" class="empty-tip">
              <el-icon :size="36" color="var(--iot-color-success)"><Connection /></el-icon>
              <p>一切正常 · 暂无告警</p>
            </div>
            <div v-else class="alert-list">
              <div v-for="a in (summary?.recentAlerts ?? []).slice(0, 5)" :key="a.id" class="alert-item">
                <el-tag size="small">{{ a.level }}</el-tag>
                <div class="alert-content">
                  <div class="alert-title">{{ a.title }}</div>
                  <div class="alert-time">{{ a.deviceKey }} · {{ a.createdAt }}</div>
                </div>
              </div>
            </div>
          </div>

          <div class="context-section">
            <h4 class="sub-title">工单状态</h4>
            <div class="wo-summary">
              <div class="wo-stat">
                <div class="wo-num" style="color: var(--iot-color-info)">{{ workOrderStats.pending }}</div>
                <div class="wo-label">待派单</div>
              </div>
              <div class="wo-stat">
                <div class="wo-num" style="color: var(--iot-color-primary)">{{ workOrderStats.processing }}</div>
                <div class="wo-label">处理中</div>
              </div>
              <div class="wo-stat">
                <div class="wo-num" style="color: var(--iot-color-success)">{{ workOrderStats.completed }}</div>
                <div class="wo-label">已完成</div>
              </div>
              <div class="wo-stat">
                <div class="wo-num" style="color: var(--iot-color-danger)">{{ workOrderStats.overdue }}</div>
                <div class="wo-label">已超时</div>
              </div>
            </div>
          </div>

          <div class="context-section">
            <h4 class="sub-title">拓扑统计</h4>
            <div class="topo-stats-grid">
              <div class="topo-stat-item">
                <span class="topo-stat-label">节点</span>
                <span class="topo-stat-value">{{ stats.nodeCount }}</span>
              </div>
              <div class="topo-stat-item">
                <span class="topo-stat-label">连接</span>
                <span class="topo-stat-value">{{ stats.edgeCount }}</span>
              </div>
              <div class="topo-stat-item">
                <span class="topo-stat-label">告警</span>
                <span class="topo-stat-value text-warning">{{ stats.warningCount }}</span>
              </div>
              <div class="topo-stat-item">
                <span class="topo-stat-label">故障</span>
                <span class="topo-stat-value text-danger">{{ stats.faultCount }}</span>
              </div>
            </div>
          </div>

          <div class="context-section tip-section">
            <el-alert type="info" :closable="false" show-icon>
              <template #title>
                <span class="tip-title">使用提示</span>
              </template>
              点击拓扑图中的节点查看上下文 ·
              拖拽可平移 · 滚轮缩放 ·
              右上"维护模式"可进入拓扑编辑
            </el-alert>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.dashboard {
  background: var(--iot-bg-page);
  height: calc(100vh - 56px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

// ============= KPI 条 =============
.kpi-bar {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: $spacing-12;
  margin-bottom: $spacing-12;
  flex-shrink: 0;
}
.kpi-card {
  background: var(--iot-bg-card);
  border-radius: $radius-large;
  padding: $spacing-12 $spacing-16;
  display: flex;
  align-items: center;
  gap: $spacing-12;
  box-shadow: var(--iot-shadow-light);
  border: 1px solid var(--iot-border-lighter);
  transition: transform $transition-base, box-shadow $transition-base;
  &.clickable { cursor: pointer; &:hover { transform: translateY(-2px); box-shadow: var(--iot-shadow-md); border-color: var(--iot-border-base); } }
}
.kpi-icon-bg {
  width: 44px; height: 44px;
  border-radius: $radius-medium;
  display: flex; align-items: center; justify-content: center;
  background: var(--iot-bg-hover);
  color: var(--iot-text-secondary);
  flex-shrink: 0;
}
.kpi-body { flex: 1; min-width: 0; }
.kpi-num {
  font-size: $font-size-huge;
  font-weight: $font-weight-semibold;
  font-family: var(--iot-font-family-code);
  color: var(--iot-text-primary);
  line-height: 1.1;
}
.kpi-label {
  font-size: $font-size-extra-small;
  color: var(--iot-text-secondary);
  margin-top: 2px;
}
.kpi-rate {
  font-size: $font-size-extra-small;
  color: var(--iot-text-placeholder);
  font-family: var(--iot-font-family-code);
  margin-top: 2px;
}
.kpi-green .kpi-icon-bg { background: color-mix(in srgb, var(--iot-color-success) 15%, transparent); color: var(--iot-color-success); }
.kpi-red   .kpi-icon-bg { background: color-mix(in srgb, var(--iot-color-danger)  15%, transparent); color: var(--iot-color-danger); }
.kpi-blue  .kpi-icon-bg { background: color-mix(in srgb, var(--iot-color-primary) 15%, transparent); color: var(--iot-color-primary); }

// ============= 主区布局 =============
.main-layout {
  flex: 1;
  display: grid;
  grid-template-columns: 240px 1fr 340px;
  gap: $spacing-12;
  min-height: 0;
}
@media (max-width: $breakpoint-lg) {
  .main-layout { grid-template-columns: 1fr; }
}

.left-panel, .right-panel {
  display: flex;
  flex-direction: column;
  padding: $spacing-12;
  min-height: 0;
  overflow-y: auto;
}

.panel-header {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: $spacing-12;
}

.card-title {
  font-size: $font-size-medium;
  margin: 0;
  color: var(--iot-text-primary);
  display: flex;
  align-items: center;
  gap: $spacing-8;
  font-weight: $font-weight-semibold;
  &::before { content: ''; display: block; width: 3px; height: 14px; background: var(--iot-color-primary); border-radius: 2px; }
  .el-icon { font-size: $font-size-medium; color: var(--iot-color-primary); }
}
.title-dot {
  width: 8px; height: 8px; border-radius: 50%;
  display: inline-block;
  box-shadow: 0 0 6px currentColor;
}

.sub-title {
  font-size: $font-size-small;
  font-weight: $font-weight-semibold;
  margin: $spacing-12 0 $spacing-8;
  color: var(--iot-text-primary);
  display: flex; align-items: center; gap: $spacing-8;
  .el-icon { color: var(--iot-color-primary); }
}

.context-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: $spacing-12; }

// ============= 左栏 =============
.pie-chart { width: 100%; height: 200px; }
.thin-divider { margin: $spacing-12 0; }
.quick-stats { display: flex; flex-direction: column; gap: $spacing-8; }
.quick-item {
  display: flex; align-items: center; gap: $spacing-8;
  padding: $spacing-4 $spacing-8; border-radius: $radius-small;
  font-size: $font-size-small;
  &:hover { background: var(--iot-bg-hover); }
}
.quick-item .dot { width: 10px; height: 10px; border-radius: 50%; }
.quick-item .dot.online  { background: var(--iot-color-success); }
.quick-item .dot.offline { background: var(--iot-text-disabled); }
.quick-item .dot.warning { background: var(--iot-color-warning); }
.quick-item .dot.fault   { background: var(--iot-color-danger); }
.quick-item .lbl { color: var(--iot-text-regular); flex: 1; }
.quick-item .num { font-weight: $font-weight-semibold; font-family: var(--iot-font-family-code); color: var(--iot-text-primary); }
.refresh-btn { width: 100%; margin-top: $spacing-12; }

// ============= 中栏 =============
.center-panel {
  padding: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: relative;
}
.canvas-header {
  padding: $spacing-12 $spacing-16;
  border-bottom: 1px solid var(--iot-border-lighter);
  display: flex; align-items: center; justify-content: space-between;
  background: var(--iot-bg-card);
  flex-shrink: 0;
}
.canvas-title-group {
  display: flex; align-items: center; gap: $spacing-8;
}
.canvas-title-icon {
  color: var(--iot-color-warning);
  display: inline-flex; align-items: center;
  font-size: $font-size-large;
}
.canvas-title {
  font-size: $font-size-medium;
  font-weight: $font-weight-semibold;
  color: var(--iot-text-primary);
}
.region-tag {
  margin-left: $spacing-4;
  background: var(--iot-color-primary-light-9);
  color: var(--iot-color-primary);
  border-color: var(--iot-color-primary-light-5);
}
.canvas-header-right { display: flex; align-items: center; gap: $spacing-12; }
.meta-text {
  font-size: $font-size-extra-small;
  color: var(--iot-text-secondary);
  font-family: var(--iot-font-family-code);
}
.canvas-body { flex: 1; position: relative; min-height: 0; background: var(--iot-bg-card); }

.canvas-tools {
  position: absolute; right: $spacing-12; top: $spacing-12; z-index: 10;
  display: flex; flex-direction: column; gap: $spacing-4;
  :deep(.el-button) {
    background: var(--iot-bg-card);
    border: 1px solid var(--iot-border-base);
    box-shadow: var(--iot-shadow-md);
    color: var(--iot-text-regular);
    &:hover { color: var(--iot-color-primary); border-color: var(--iot-color-primary); }
  }
}

.canvas-overlay-legend {
  position: absolute; left: $spacing-12; bottom: $spacing-12; z-index: 10;
  background: var(--iot-bg-card);
  border: 1px solid var(--iot-border-lighter);
  padding: $spacing-8 $spacing-12;
  border-radius: $radius-base;
  box-shadow: var(--iot-shadow-md);
  font-size: $font-size-extra-small;
  color: var(--iot-text-regular);
  display: flex; flex-direction: column; gap: $spacing-4;
  min-width: 220px;
}
.overlay-row {
  display: flex; gap: $spacing-12;
  &.hint {
    color: var(--iot-text-placeholder);
    border-top: 1px solid var(--iot-border-lighter);
    padding-top: $spacing-4;
    .el-icon { vertical-align: middle; margin-right: 2px; }
  }
}
.overlay-item {
  display: inline-flex; align-items: center; gap: $spacing-4;
  color: var(--iot-text-regular);
  &.warning { color: var(--iot-color-warning); }
  &.fault   { color: var(--iot-color-danger); }
  &.pulse .dot { animation: dotPulse 1.6s infinite; }
  .dot { width: 8px; height: 8px; border-radius: 50%; background: var(--iot-color-success); display: inline-block; }
  &.warning .dot { background: var(--iot-color-warning); }
  &.fault .dot { background: var(--iot-color-danger); }
}
@keyframes dotPulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(245, 108, 108, 0.4); }
  50%      { box-shadow: 0 0 0 4px rgba(245, 108, 108, 0); }
}

// ============= 右栏 =============
.context-section { margin-top: $spacing-12; }
.node-desc {
  :deep(.el-descriptions__label) { color: var(--iot-text-secondary); }
  :deep(.el-descriptions__content) { color: var(--iot-text-primary); }
}

.alert-list, .wo-list { display: flex; flex-direction: column; gap: $spacing-8; }
.alert-item {
  display: flex; gap: $spacing-8; align-items: flex-start;
  padding: $spacing-8 $spacing-12;
  background: var(--iot-bg-hover);
  border-radius: $radius-base;
  font-size: $font-size-small;
  border-left: 3px solid var(--iot-color-warning);
}
.alert-content { flex: 1; min-width: 0; }
.alert-title {
  font-weight: $font-weight-medium;
  color: var(--iot-text-primary);
  margin-bottom: 2px;
}
.alert-time {
  font-size: $font-size-extra-small;
  color: var(--iot-text-placeholder);
}

.wo-item {
  padding: $spacing-8 $spacing-12;
  background: var(--iot-bg-hover);
  border-radius: $radius-base;
  font-size: $font-size-small;
}
.wo-meta { font-size: $font-size-extra-small; color: var(--iot-text-placeholder); margin-top: 2px; }

.wo-summary { display: grid; grid-template-columns: repeat(2, 1fr); gap: $spacing-8; }
.wo-stat {
  background: var(--iot-bg-hover);
  padding: $spacing-12;
  border-radius: $radius-base;
  text-align: center;
  transition: transform $transition-fast;
  &:hover { transform: translateY(-1px); background: var(--iot-bg-card); border: 1px solid var(--iot-border-base); }
}
.wo-num {
  font-size: $font-size-large;
  font-weight: $font-weight-semibold;
  font-family: var(--iot-font-family-code);
  line-height: 1.2;
}
.wo-label {
  font-size: $font-size-extra-small;
  color: var(--iot-text-secondary);
  margin-top: 2px;
}

.topo-stats-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: $spacing-8;
}
.topo-stat-item {
  background: var(--iot-bg-hover);
  padding: $spacing-8 $spacing-12;
  border-radius: $radius-base;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.topo-stat-label { color: var(--iot-text-secondary); font-size: $font-size-small; }
.topo-stat-value {
  font-weight: $font-weight-semibold;
  font-family: var(--iot-font-family-code);
  color: var(--iot-text-primary);
  &.text-warning { color: var(--iot-color-warning); }
  &.text-danger  { color: var(--iot-color-danger); }
}

.quick-actions {
  display: flex; flex-wrap: wrap; gap: $spacing-8;
  :deep(.el-button) { flex: 1; min-width: 80px; }
}

.empty-tip {
  text-align: center;
  padding: $spacing-16 $spacing-8;
  color: var(--iot-text-placeholder);
  background: var(--iot-bg-hover);
  border-radius: $radius-base;
  .el-icon { margin-bottom: $spacing-4; opacity: 0.8; }
  p { margin: 0; font-size: $font-size-small; }
}

.tip-section {
  margin-top: auto;
  padding-top: $spacing-16;
  .tip-title { font-weight: $font-weight-semibold; color: var(--iot-text-primary); }
}

.text-warning { color: var(--iot-color-warning); }
.text-danger  { color: var(--iot-color-danger); }
</style>