<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, computed, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  Connection, Warning, Refresh, FullScreen, Aim,
  SetUp, Histogram, Share, Plus, Minus
} from '@element-plus/icons-vue'
import G6 from '@antv/g6'
import {
  getTopologyGraph,
  listTopologyRegions,
  type TopologyGraph,
  type TopologyRegionVO,
  type TopologyNode,
  type TopologyEdge,
  type NodeStatus,
  type TopologyNodeType
} from '@/api/monitor/topology'

const router = useRouter()
const loading = ref(false)
const graphLoading = ref(true)

// 区域 / 布局
const regions = ref<TopologyRegionVO[]>([])
const activeRegionId = ref<string>('')
const layoutType = ref<'dagre' | 'force' | 'circular'>('dagre')

// 拓扑数据
const graphData = ref<TopologyGraph | null>(null)
const selectedNode = ref<TopologyNode | null>(null)

// G6 实例
let graph: any = null
const graphEl = ref<HTMLDivElement>()

// ============= 状态色板 =============
const statusColor: Record<NodeStatus, { fill: string; stroke: string; tag: string; label: string }> = {
  normal:  { fill: '#67c23a', stroke: '#529b2e', tag: 'success', label: '正常' },
  warning: { fill: '#e6a23c', stroke: '#b88230', tag: 'warning', label: '警告' },
  fault:   { fill: '#f56c6c', stroke: '#c45656', tag: 'danger',  label: '故障' },
  offline: { fill: '#909399', stroke: '#73767a', tag: 'info',    label: '离线' }
}

const nodeTypeMeta: Record<TopologyNodeType, { label: string; symbol: string; shape: string; color: string }> = {
  substation:  { label: '变电站', symbol: '⚡', shape: 'rect',     color: '#409eff' },
  transformer: { label: '变压器', symbol: 'T',  shape: 'diamond',  color: '#0d9488' },
  busbar:      { label: '母线',   symbol: '─', shape: 'rect',     color: '#303133' },
  switch:      { label: '开关',   symbol: '×', shape: 'circle',   color: '#67c23a' },
  ring_main:   { label: '环网柜', symbol: '柜', shape: 'rect',     color: '#409eff' },
  junction:    { label: '分接箱', symbol: '┬', shape: 'triangle', color: '#909399' },
  meter:       { label: '用户',   symbol: '用', shape: 'circle',   color: '#909399' }
}

// ============= Mock 数据 =============
function buildMockGraph(regionId?: string): TopologyGraph {
  const nodes: TopologyNode[] = [
    { id: 'SUB-01',  name: '朝阳变电站',  type: 'substation',  voltageLevel: '110kV', status: 'normal',  region: '北京-朝阳' },
    { id: 'TR-01',   name: '#1 主变',    type: 'transformer', voltageLevel: '110kV', status: 'normal',  region: '北京-朝阳' },
    { id: 'BUS-10A', name: '10kV 母线 A', type: 'busbar',      voltageLevel: '10kV',  status: 'normal',  region: '北京-朝阳' },
    { id: 'BUS-10B', name: '10kV 母线 B', type: 'busbar',      voltageLevel: '10kV',  status: 'normal',  region: '北京-朝阳' },
    { id: 'SW-01',   name: '出线开关 1', type: 'switch',      voltageLevel: '10kV',  status: 'normal',  region: '北京-朝阳' },
    { id: 'SW-02',   name: '出线开关 2', type: 'switch',      voltageLevel: '10kV',  status: 'normal',  region: '北京-朝阳' },
    { id: 'RM-A1',   name: '环网柜 A-1', type: 'ring_main',   voltageLevel: '10kV',  status: 'warning', region: '北京-朝阳', deviceId: 101 },
    { id: 'RM-A2',   name: '环网柜 A-2', type: 'ring_main',   voltageLevel: '10kV',  status: 'normal',  region: '北京-朝阳', deviceId: 102 },
    { id: 'RM-B1',   name: '环网柜 B-1', type: 'ring_main',   voltageLevel: '10kV',  status: 'fault',   region: '北京-朝阳', deviceId: 103 },
    { id: 'RM-B2',   name: '环网柜 B-2', type: 'ring_main',   voltageLevel: '10kV',  status: 'normal',  region: '北京-朝阳', deviceId: 104 },
    { id: 'JB-01',   name: '分接箱 01',  type: 'junction',    voltageLevel: '10kV',  status: 'normal',  region: '北京-朝阳' },
    { id: 'USR-01',  name: '国贸三期',   type: 'meter',       voltageLevel: '0.4kV', status: 'normal',  region: '北京-朝阳' },
    { id: 'USR-02',  name: '万达广场',   type: 'meter',       voltageLevel: '0.4kV', status: 'offline', region: '北京-朝阳' }
  ]
  const edges: TopologyEdge[] = [
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
  const filtered = regionId ? nodes.filter(n => n.region === regionId) : nodes
  const filteredIds = new Set(filtered.map(n => n.id))
  const filteredEdges = edges.filter(e => filteredIds.has(e.source) && filteredIds.has(e.target))
  return {
    region: regionId || 'all',
    rootNodeId: 'SUB-01',
    nodes: filtered,
    edges: filteredEdges,
    stats: {
      nodeCount: filtered.length,
      edgeCount: filteredEdges.length,
      faultCount: filtered.filter(n => n.status === 'fault').length,
      warningCount: filtered.filter(n => n.status === 'warning').length
    }
  }
}

// ============= G6 自定义节点(性能 + 可读性兼顾) =============
//
// 关键:
// 1. 每个节点 3 个 shape: 主体 + 符号文字 + 名称背景+文字(名称加白底以保证可读)
// 2. 完全去掉 shadowBlur(性能杀手)
// 3. 名称下方加白底小卡片,在任何背景下都清晰
// 4. 字体:符号 10px 白色加粗 / 名称 11px 深色 #303133
//
function registerNodes() {
  // 通用 helper:画名称徽章(白底 + 文字),保证可读
  function drawLabel(group: any, name: string, yOffset: number) {
    const text = name || ''
    const w = Math.max(40, text.length * 11 + 10)
    group.addShape('rect', {
      attrs: {
        x: -w / 2, y: yOffset - 8,
        width: w, height: 16,
        fill: '#ffffff',
        stroke: '#dcdfe6',
        lineWidth: 1,
        radius: 3
      }
    })
    group.addShape('text', {
      attrs: {
        x: 0, y: yOffset + 3,
        text,
        fontSize: 11,
        fill: '#303133',
        fontWeight: 500,
        textAlign: 'center'
      }
    })
  }

  // 圆形节点(开关 / 用户)
  G6.registerNode('device-circle', {
    draw(cfg: any, group: any) {
      const status = cfg.status as NodeStatus
      const color = statusColor[status]
      const isEmphasis = status === 'fault' || status === 'warning'
      const keyShape = group.addShape('circle', {
        attrs: {
          x: 0, y: 0, r: 14,
          fill: color.fill,
          stroke: '#fff',
          lineWidth: isEmphasis ? 3 : 2
        }
      })
      // 符号
      group.addShape('text', {
        attrs: {
          x: 0, y: 3,
          text: cfg.symbol || '·',
          fontSize: 10,
          fontWeight: 600,
          fill: '#fff',
          textAlign: 'center'
        }
      })
      // 名称徽章
      drawLabel(group, cfg.label, 24)
      return keyShape
    }
  }, 'circle')

  // 矩形节点(环网柜 / 母线 / 变电站)
  G6.registerNode('device-rect', {
    draw(cfg: any, group: any) {
      const status = cfg.status as NodeStatus
      const color = statusColor[status]
      const isEmphasis = status === 'fault' || status === 'warning'
      const isBusbar = cfg.type === 'busbar'
      const isSubstation = cfg.type === 'substation'
      const w = isSubstation ? 56 : isBusbar ? 50 : 40
      const h = isBusbar ? 16 : 30
      const keyShape = group.addShape('rect', {
        attrs: {
          x: -w / 2, y: -h / 2, w, h,
          fill: isBusbar ? color.stroke : color.fill,
          stroke: '#fff',
          lineWidth: isEmphasis ? 3 : 2,
          radius: 3
        }
      })
      if (isBusbar) {
        group.addShape('line', {
          attrs: {
            x1: -w / 2 + 4, y1: 0, x2: w / 2 - 4, y2: 0,
            stroke: '#fff', lineWidth: 1.5
          }
        })
      } else {
        group.addShape('text', {
          attrs: {
            x: 0, y: 3,
            text: cfg.symbol || '',
            fontSize: 11,
            fontWeight: 600,
            fill: '#fff',
            textAlign: 'center'
          }
        })
      }
      // 名称徽章(下方)
      drawLabel(group, cfg.label, h / 2 + 18)
      return keyShape
    }
  }, 'rect')

  // 菱形节点(变压器)
  G6.registerNode('device-diamond', {
    draw(cfg: any, group: any) {
      const status = cfg.status as NodeStatus
      const color = statusColor[status]
      const isEmphasis = status === 'fault' || status === 'warning'
      const s = 28
      const keyShape = group.addShape('polygon', {
        attrs: {
          points: [[0, -s / 2], [s / 2, 0], [0, s / 2], [-s / 2, 0]],
          fill: color.fill,
          stroke: '#fff',
          lineWidth: isEmphasis ? 3 : 2
        }
      })
      group.addShape('text', {
        attrs: {
          x: 0, y: 3,
          text: cfg.symbol || 'T',
          fontSize: 11,
          fontWeight: 600,
          fill: '#fff',
          textAlign: 'center'
        }
      })
      // 名称徽章
      drawLabel(group, cfg.label, s / 2 + 22)
      return keyShape
    }
  }, 'polygon')
}

// ============= G6 实例初始化 =============
async function initGraph() {
  if (!graphEl.value) return
  registerNodes()
  graphLoading.value = true

  const { clientWidth: width, clientHeight: height } = graphEl.value

  graph = new (G6 as any).Graph({
    container: graphEl.value,
    width,
    height,
    fitView: true,
    fitViewPadding: 30,
    // === 性能关键配置 ===
    animate: false,         // 关闭补间动画,拖动实时重绘
    animateCfg: { duration: 0 },
    pixelRatio: 1,          // 固定 1 倍像素(放弃 retina 锐度换性能,拓扑图无小字)
    // ===================
    defaultNode: {
      type: 'device-circle',
      size: 32,
      style: { cursor: 'pointer' }
    },
    defaultEdge: {
      type: 'line',
      style: {
        stroke: '#909399',
        lineWidth: 1.2,
        cursor: 'pointer'
      },
      // 默认不显示标签,hover 才显示
      labelCfg: { style: { fontSize: 10 } }
    },
    modes: {
      default: ['drag-canvas', 'zoom-canvas', 'drag-node']
    },
    // 边 hover 时才显示标签
    edgeStateStyles: {
      hover: { lineWidth: 2.5, stroke: '#409eff' }
    },
    layout: getLayoutConfig()
  })

  graph.on('node:click', (evt: any) => {
    const item = evt.item
    const model = item.getModel()
    onNodeClick(model as TopologyNode)
  })

  graph.on('edge:mouseenter', (evt: any) => {
    const item = evt.item
    graph?.setItemState(item, 'hover', true)
    // 显示这条边的 label
    const model = item.getModel()
    graph?.updateItem(item, { label: model.label })
  })

  graph.on('edge:mouseleave', (evt: any) => {
    const item = evt.item
    graph?.setItemState(item, 'hover', false)
    graph?.updateItem(item, { label: '' })
  })

  graph.on('canvas:click', () => {
    selectedNode.value = null
  })

  graphLoading.value = false
}

function getLayoutConfig(): any {
  switch (layoutType.value) {
    case 'dagre':
      return {
        type: 'dagre',
        rankdir: 'TB',
        align: 'UL',
        nodesepFunc: (d: any) => (d.type === 'substation' ? 50 : 28),
        ranksepFunc: (d: any) => (d.type === 'substation' ? 80 : 50),
        preventOverlap: true
      }
    case 'force':
      return {
        type: 'force',
        preventOverlap: true,
        nodeStrength: -60,
        edgeStrength: 0.6,
        collideStrength: 0.9,
        alphaDecay: 0.05  // 更快收敛
      }
    case 'circular':
      return { type: 'circular' }
    default:
      return { type: 'dagre' }
  }
}

async function loadRegions() {
  try {
    const res: any = await listTopologyRegions()
    regions.value = res.data ?? []
  } catch {
    regions.value = [
      { id: '北京-朝阳', name: '北京·朝阳供电区', nodeCount: 13, faultCount: 1 },
      { id: '北京-海淀', name: '北京·海淀供电区', nodeCount: 0, faultCount: 0 }
    ]
  }
  if (regions.value.length > 0 && !activeRegionId.value) {
    activeRegionId.value = regions.value[0].id
    await loadTopology()
  }
}

async function loadTopology() {
  loading.value = true
  try {
    const res: any = await getTopologyGraph(activeRegionId.value)
    graphData.value = res.data ?? buildMockGraph(activeRegionId.value)
  } catch {
    graphData.value = buildMockGraph(activeRegionId.value)
  } finally {
    loading.value = false
  }
  await nextTick()
  renderGraph()
}

function renderGraph() {
  if (!graph || !graphData.value) return
  const data = {
    nodes: graphData.value.nodes.map(n => ({
      ...n,
      // label 是 G6 节点内置的 label 字段,自定义节点在 draw() 里通过 cfg.label 拿到
      label: n.name,
      symbol: nodeTypeMeta[n.type].symbol,
      type: nodeTypeMeta[n.type].shape === 'circle' ? 'device-circle'
        : nodeTypeMeta[n.type].shape === 'diamond' ? 'device-diamond'
        : 'device-rect'
    })),
    edges: graphData.value.edges.map(e => ({
      ...e,
      label: '',  // 默认空,hover 才填
      style: {
        stroke: statusColor[e.status].stroke,
        lineWidth: e.type === 'tie' ? 1 : (e.type === 'bus' ? 2 : 1.5),
        lineDash: e.type === 'tie' ? [4, 4] : undefined,
        opacity: 0.85
      }
    }))
  }
  graph.data(data)
  graph.render()
  graph.fitView(30)
}

function onNodeClick(node: TopologyNode) {
  selectedNode.value = node
}

function goDeviceDetail(node: TopologyNode) {
  if (node.deviceId) {
    router.push(`/device/list?deviceId=${node.deviceId}`)
  }
}

function onLayoutChange() {
  if (!graph || !graphData.value) return
  // 切换布局:重新 render,简单可靠
  renderGraph()
}

function onZoomIn()  { if (graph) graph.zoomTo(graph.getZoom() * 1.2) }
function onZoomOut() { if (graph) graph.zoomTo(graph.getZoom() / 1.2) }
function onFitView() { if (graph) graph.fitView(30) }

function onFullScreen() {
  if (!graphEl.value) return
  const el = graphEl.value.parentElement?.parentElement
  if (!el) return
  if (document.fullscreenElement) document.exitFullscreen()
  else el.requestFullscreen()
}

const stats = computed(() => graphData.value?.stats ?? { nodeCount: 0, edgeCount: 0, faultCount: 0, warningCount: 0 })

function handleResize() {
  if (!graph || !graphEl.value) return
  graph.changeSize(graphEl.value.clientWidth, graphEl.value.clientHeight)
  graph.fitView(30)
}

watch(layoutType, onLayoutChange)
watch(activeRegionId, loadTopology)

onMounted(async () => {
  await initGraph()
  await loadRegions()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  if (graph) {
    graph.destroy()
    graph = null
  }
})
</script>

<template>
  <div class="page-container topo-page" v-loading="loading">
    <div class="page-header">
      <h2 class="page-title">电网拓扑 · 节点图</h2>
      <div class="header-tools">
        <el-select v-model="activeRegionId" placeholder="选择区域" style="width: 200px" @change="loadTopology">
          <el-option
            v-for="r in regions"
            :key="r.id"
            :label="`${r.name} (${r.nodeCount})`"
            :value="r.id"
          />
        </el-select>
        <el-radio-group v-model="layoutType" size="default">
          <el-radio-button value="dagre">层级</el-radio-button>
          <el-radio-button value="force">力导向</el-radio-button>
          <el-radio-button value="circular">环形</el-radio-button>
        </el-radio-group>
        <el-button :icon="Refresh" @click="loadTopology">刷新</el-button>
      </div>
    </div>

    <!-- 统计卡 -->
    <el-row :gutter="12" class="mb-12">
      <el-col :xs="6" :sm="6">
        <div class="stat-mini">
          <div class="stat-mini-num">{{ stats.nodeCount }}</div>
          <div class="stat-mini-label">节点数</div>
        </div>
      </el-col>
      <el-col :xs="6" :sm="6">
        <div class="stat-mini">
          <div class="stat-mini-num">{{ stats.edgeCount }}</div>
          <div class="stat-mini-label">连接数</div>
        </div>
      </el-col>
      <el-col :xs="6" :sm="6">
        <div class="stat-mini" :class="{ alert: stats.warningCount > 0 }">
          <div class="stat-mini-num">{{ stats.warningCount }}</div>
          <div class="stat-mini-label">告警</div>
        </div>
      </el-col>
      <el-col :xs="6" :sm="6">
        <div class="stat-mini" :class="{ danger: stats.faultCount > 0 }">
          <div class="stat-mini-num">{{ stats.faultCount }}</div>
          <div class="stat-mini-label">故障</div>
        </div>
      </el-col>
    </el-row>

    <div class="topo-layout">
      <!-- 左:图例 + 节点列表 -->
      <div class="page-card left-panel">
        <h3 class="card-title">设备类型</h3>
        <div class="legend-list">
          <div v-for="(m, t) in nodeTypeMeta" :key="t" class="legend-item">
            <span class="legend-shape" :class="`shape-${m.shape}`" :style="{ background: m.color }">
              {{ m.symbol }}
            </span>
            <span class="legend-label">{{ m.label }}</span>
          </div>
        </div>

        <h3 class="card-title mt-16">节点列表 ({{ graphData?.nodes.length ?? 0 }})</h3>
        <div class="node-list">
          <div v-for="n in graphData?.nodes"
               :key="n.id"
               class="node-item"
               :class="{ active: selectedNode?.id === n.id }"
               @click="onNodeClick(n)">
            <span class="status-dot" :style="{ background: statusColor[n.status].fill }"></span>
            <div class="node-info">
              <div class="node-name">{{ n.name }}</div>
              <div class="node-meta">
                <el-tag size="small" effect="plain">{{ nodeTypeMeta[n.type].label }}</el-tag>
                <span class="voltage">{{ n.voltageLevel }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 中:画布 -->
      <div class="page-card center-panel">
        <div ref="graphEl" class="graph-canvas"></div>

        <div v-if="graphLoading" class="canvas-overlay">
          <el-icon class="is-loading" :size="32"><Refresh /></el-icon>
          <p>画布加载中...</p>
        </div>

        <div class="canvas-tools">
          <el-button :icon="Plus"   size="small" circle @click="onZoomIn"  title="放大" />
          <el-button :icon="Minus"  size="small" circle @click="onZoomOut" title="缩小" />
          <el-button :icon="Aim"    size="small" circle @click="onFitView" title="自适应" />
          <el-button :icon="FullScreen" size="small" circle @click="onFullScreen" title="全屏" />
        </div>

        <div class="status-legend">
          <div class="legend-row">
            <span class="legend-item-mini"><span class="dot normal"></span>正常</span>
            <span class="legend-item-mini"><span class="dot warning"></span>警告</span>
            <span class="legend-item-mini"><span class="dot fault"></span>故障</span>
            <span class="legend-item-mini"><span class="dot offline"></span>离线</span>
          </div>
          <div class="legend-row">
            <span class="legend-item-mini"><span class="line-solid"></span>实线:馈线/母线</span>
            <span class="legend-item-mini"><span class="line-dashed"></span>虚线:联络线</span>
            <span class="legend-item-mini legend-tip">边 hover 显示线缆编号</span>
          </div>
        </div>
      </div>

      <!-- 右:详情 -->
      <div class="page-card right-panel">
        <template v-if="selectedNode">
          <h3 class="card-title">{{ selectedNode.name }}</h3>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="节点 ID">
              <code>{{ selectedNode.id }}</code>
            </el-descriptions-item>
            <el-descriptions-item label="设备类型">
              <el-tag size="small">{{ nodeTypeMeta[selectedNode.type].label }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="电压等级">
              <el-tag size="small" type="warning">{{ selectedNode.voltageLevel }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="运行状态">
              <el-tag :type="statusColor[selectedNode.status].tag as any" size="small">
                {{ statusColor[selectedNode.status].label }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="所在区域">{{ selectedNode.region || '—' }}</el-descriptions-item>
            <el-descriptions-item v-if="selectedNode.substationCode" label="子站编号">
              {{ selectedNode.substationCode }}
            </el-descriptions-item>
            <el-descriptions-item v-if="selectedNode.deviceId" label="关联设备">
              <el-link type="primary" :underline="false" @click="goDeviceDetail(selectedNode)">
                设备 #{{ selectedNode.deviceId }} →
              </el-link>
            </el-descriptions-item>
          </el-descriptions>

          <div class="alert-zone">
            <h4 class="sub-title">最近告警</h4>
            <el-alert type="info" :closable="false" show-icon>
              告警关联功能待后端 <code>/monitor/topology/node/{id}</code> 就绪后启用。
            </el-alert>
          </div>
        </template>
        <el-empty v-else description="点击节点查看详情" :image-size="80" />
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.topo-page { background: var(--iot-bg-page); height: calc(100vh - 56px); display: flex; flex-direction: column; }
.page-header { display: flex; align-items: center; gap: $spacing-12; margin-bottom: $spacing-12; .page-title { margin: 0; flex: 1; } }
.header-tools { display: flex; align-items: center; gap: $spacing-12; }
.mb-12 { margin-bottom: $spacing-12; }
.mt-16 { margin-top: $spacing-16; }

.stat-mini {
  background: var(--iot-bg-card); border-radius: $radius-base;
  padding: $spacing-8 $spacing-12;
  display: flex; align-items: center; gap: $spacing-8;
  box-shadow: var(--iot-shadow-light);
  &.alert { background: linear-gradient(135deg, var(--iot-color-warning-light) 0%, #fff 100%); .stat-mini-num { color: var(--iot-color-warning); } }
  &.danger { background: linear-gradient(135deg, var(--iot-color-danger-light) 0%, #fff 100%); .stat-mini-num { color: var(--iot-color-danger); } }
}
.stat-mini-num { font-size: $font-size-large; font-weight: $font-weight-semibold; font-family: var(--iot-font-family-code); color: var(--iot-text-primary); }
.stat-mini-label { font-size: $font-size-extra-small; color: var(--iot-text-secondary); }

.topo-layout { flex: 1; display: grid; grid-template-columns: 280px 1fr 320px; gap: $spacing-12; min-height: 0; }
@media (max-width: $breakpoint-lg) { .topo-layout { grid-template-columns: 1fr; } }

.left-panel, .right-panel { display: flex; flex-direction: column; padding: $spacing-12; min-height: 0; overflow: hidden; }

.card-title {
  font-size: $font-size-medium; margin: 0 0 $spacing-12;
  color: var(--iot-text-primary); display: flex; align-items: center; gap: $spacing-8;
  &::before { content: ''; display: block; width: 3px; height: 14px; background: var(--iot-color-primary); }
}
.sub-title { font-size: $font-size-small; font-weight: $font-weight-semibold; margin: $spacing-16 0 $spacing-8; color: var(--iot-text-primary); }

.legend-list { display: flex; flex-direction: column; gap: $spacing-8; }
.legend-item { display: flex; align-items: center; gap: $spacing-8; font-size: $font-size-small; }
.legend-shape {
  width: 24px; height: 24px;
  display: inline-flex; align-items: center; justify-content: center;
  color: #fff; font-weight: 600; font-size: 11px;
  border-radius: $radius-small;
  border: 2px solid #fff;
  box-shadow: var(--iot-shadow-light);
  flex-shrink: 0;
  &.shape-circle   { border-radius: 50%; }
  &.shape-diamond  { transform: rotate(45deg); border-radius: 2px; }
  &.shape-rect     { border-radius: $radius-small; }
  &.shape-triangle { border-radius: 0; clip-path: polygon(50% 0, 100% 100%, 0 100%); }
}
.legend-label { color: var(--iot-text-regular); }

.node-list { flex: 1; overflow-y: auto; margin-top: $spacing-8; }
.node-item {
  display: flex; align-items: center; gap: $spacing-8;
  padding: $spacing-8; border-radius: $radius-base;
  cursor: pointer; transition: background $transition-fast;
  border: 1px solid transparent;
  margin-bottom: $spacing-4;
  &:hover { background: var(--iot-bg-hover); }
  &.active { background: var(--iot-color-primary-light-9); border-color: var(--iot-color-primary-light-5); }
}
.status-dot { width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0; }
.node-info { flex: 1; min-width: 0; }
.node-name { font-size: $font-size-small; font-weight: $font-weight-medium; }
.node-meta { display: flex; align-items: center; gap: $spacing-4; margin-top: 2px; }
.voltage { font-size: $font-size-extra-small; color: var(--iot-text-secondary); font-family: var(--iot-font-family-code); }

.center-panel { padding: 0; position: relative; overflow: hidden; }
.graph-canvas { width: 100%; height: 100%; min-height: 480px; background: #fafbfc; }
.canvas-overlay {
  position: absolute; inset: 0;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  background: rgba(255,255,255,0.92); gap: $spacing-12; color: var(--iot-text-secondary);
  z-index: 5;
}
.canvas-tools {
  position: absolute; right: $spacing-12; top: $spacing-12; z-index: 10;
  display: flex; flex-direction: column; gap: $spacing-4;
  :deep(.el-button) { background: var(--iot-bg-card); box-shadow: var(--iot-shadow-md); }
}
.status-legend {
  position: absolute; left: $spacing-12; bottom: $spacing-12; z-index: 10;
  background: var(--iot-bg-card); padding: $spacing-8 $spacing-12;
  border-radius: $radius-base; box-shadow: var(--iot-shadow-md);
  font-size: $font-size-extra-small;
}
.legend-row { display: flex; gap: $spacing-12; margin: 2px 0; }
.legend-item-mini { display: inline-flex; align-items: center; gap: $spacing-4; color: var(--iot-text-regular); }
.legend-item-mini .dot { width: 10px; height: 10px; border-radius: 50%; }
.legend-item-mini .dot.normal  { background: #67c23a; }
.legend-item-mini .dot.warning { background: #e6a23c; }
.legend-item-mini .dot.fault   { background: #f56c6c; }
.legend-item-mini .dot.offline { background: #909399; }
.legend-item-mini .line-solid  { width: 18px; height: 2px; background: #909399; }
.legend-item-mini .line-dashed { width: 18px; height: 2px; background: repeating-linear-gradient(90deg, #909399 0 4px, transparent 4px 8px); }
.legend-tip { color: var(--iot-text-placeholder); font-style: italic; }

.alert-zone { margin-top: $spacing-16; }
</style>