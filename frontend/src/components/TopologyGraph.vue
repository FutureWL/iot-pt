<script setup lang="ts">
/**
 * 拓扑图组件 - 共享 G6 渲染逻辑
 *
 * 主题适配:
 * - 画布背景透明,父容器颜色穿透(亮色卡片白 / 暗色卡片深)
 * - 节点下方徽章 / 边 / 描边色,运行时从 CSS 变量读取
 * - 主题切换(html.dark)时通过 MutationObserver 自动重渲染
 *
 * 用法:
 *   <TopologyGraph
 *     :nodes="nodes"
 *     :edges="edges"
 *     layout="dagre"
 *     :readonly="true"
 *     height="100%"
 *     @node-click="onNodeClick"
 *     @node-double-click="onNodeEdit"
 *   />
 */
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import G6 from '@antv/g6'

export type TopologyNodeType =
  | 'substation' | 'transformer' | 'busbar' | 'switch'
  | 'ring_main' | 'junction' | 'meter'

export type VoltageLevel = '110kV' | '35kV' | '10kV' | '0.4kV'
export type NodeStatus = 'normal' | 'warning' | 'fault' | 'offline'
export type EdgeType = 'bus' | 'feeder' | 'cable' | 'tie'
export type LayoutType = 'dagre' | 'force' | 'circular'

export interface TopologyGraphNode {
  id: string
  name: string
  type: TopologyNodeType
  voltageLevel: VoltageLevel
  status: NodeStatus
  region?: string
  deviceId?: number
  substationCode?: string
}

export interface TopologyGraphEdge {
  id: string
  source: string
  target: string
  type: EdgeType
  status: NodeStatus
  label?: string
}

const props = withDefaults(defineProps<{
  nodes: TopologyGraphNode[]
  edges: TopologyGraphEdge[]
  layout?: LayoutType
  readonly?: boolean
  showLabels?: boolean
  fitViewPadding?: number
  height?: string
}>(), {
  layout: 'dagre',
  readonly: true,
  showLabels: true,
  fitViewPadding: 30,
  height: '100%'
})

const emit = defineEmits<{
  'node-click': [node: TopologyGraphNode]
  'node-double-click': [node: TopologyGraphNode]
  'edge-mouseenter': [edge: TopologyGraphEdge]
  'ready': []
}>()

// ============= 状态色板(语义色,亮/暗模式通用) =============
const statusColor: Record<NodeStatus, { fill: string; stroke: string; label: string }> = {
  normal:  { fill: '#67c23a', stroke: '#529b2e', label: '正常' },
  warning: { fill: '#e6a23c', stroke: '#b88230', label: '警告' },
  fault:   { fill: '#f56c6c', stroke: '#c45656', label: '故障' },
  offline: { fill: '#909399', stroke: '#73767a', label: '离线' }
}

const nodeTypeMeta: Record<TopologyNodeType, { label: string; symbol: string; shape: string }> = {
  substation:  { label: '变电站', symbol: '⚡', shape: 'rect' },
  transformer: { label: '变压器', symbol: 'T',  shape: 'diamond' },
  busbar:      { label: '母线',   symbol: '─', shape: 'rect' },
  switch:      { label: '开关',   symbol: '×', shape: 'circle' },
  ring_main:   { label: '环网柜', symbol: '柜', shape: 'rect' },
  junction:    { label: '分接箱', symbol: '┬', shape: 'triangle' },
  meter:       { label: '用户',   symbol: '用', shape: 'circle' }
}

// ============= 主题色(运行时从 CSS 变量读取) =============
interface ThemePalette {
  labelBg: string
  labelBorder: string
  labelText: string
  nodeStroke: string        // 节点描边色(用作 halo)
  edgeDefault: string
  canvasBg: string          // 'transparent' 让父容器穿透
}

function resolveThemeColors(): ThemePalette {
  const root = typeof document !== 'undefined' ? getComputedStyle(document.documentElement) : null
  const get = (name: string, fallback: string): string =>
    (root?.getPropertyValue(name).trim()) || fallback
  return {
    labelBg:      get('--iot-bg-card',     '#ffffff'),
    labelBorder:  get('--iot-border-base', '#dcdfe6'),
    labelText:    get('--iot-text-primary','#303133'),
    nodeStroke:   get('--iot-bg-card',     '#ffffff'), // halo 用卡片底色,亮=白,暗=深
    edgeDefault:  get('--iot-text-secondary','#909399'),
    canvasBg:     'transparent'
  }
}

const themePalette = ref<ThemePalette>(resolveThemeColors())

// 监听 html.dark 切换
let themeObserver: MutationObserver | null = null
function observeTheme() {
  if (typeof document === 'undefined') return
  themeObserver = new MutationObserver(() => {
    const newPalette = resolveThemeColors()
    // 浅色无变化则跳过重渲染
    if (JSON.stringify(newPalette) !== JSON.stringify(themePalette.value)) {
      themePalette.value = newPalette
      // 主题变了,重新渲染画布(所有 shape 重画)
      if (graph) {
        renderGraph()
      }
    }
  })
  themeObserver.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['class']
  })
}

// ============= G6 实例 =============
const graphEl = ref<HTMLDivElement>()
let graph: any = null

// ============= 自定义节点(idempotent 注册) =============
let nodesRegistered = false
function ensureNodesRegistered() {
  if (nodesRegistered) return

  function drawLabel(group: any, name: string, yOffset: number) {
    const text = name || ''
    const w = Math.max(40, text.length * 11 + 10)
    const palette = themePalette.value
    group.addShape('rect', {
      attrs: {
        x: -w / 2, y: yOffset - 8,
        width: w, height: 16,
        fill: palette.labelBg,
        stroke: palette.labelBorder,
        lineWidth: 1,
        radius: 3
      }
    })
    group.addShape('text', {
      attrs: {
        x: 0, y: yOffset + 3,
        text,
        fontSize: 11,
        fill: palette.labelText,
        fontWeight: 500,
        textAlign: 'center'
      }
    })
  }

  G6.registerNode('device-circle', {
    draw(cfg: any, group: any) {
      const status = cfg.status as NodeStatus
      const color = statusColor[status]
      const isEmphasis = status === 'fault' || status === 'warning'
      const keyShape = group.addShape('circle', {
        attrs: {
          x: 0, y: 0, r: 14,
          fill: color.fill,
          stroke: themePalette.value.nodeStroke,
          lineWidth: isEmphasis ? 3 : 2
        }
      })
      group.addShape('text', {
        attrs: { x: 0, y: 3, text: cfg.symbol || '·', fontSize: 10, fontWeight: 600, fill: '#fff', textAlign: 'center' }
      })
      if (props.showLabels) drawLabel(group, cfg.label, 24)
      return keyShape
    }
  }, 'circle')

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
          stroke: themePalette.value.nodeStroke,
          lineWidth: isEmphasis ? 3 : 2,
          radius: 3
        }
      })
      if (isBusbar) {
        group.addShape('line', {
          attrs: { x1: -w / 2 + 4, y1: 0, x2: w / 2 - 4, y2: 0, stroke: '#fff', lineWidth: 1.5 }
        })
      } else {
        group.addShape('text', {
          attrs: { x: 0, y: 3, text: cfg.symbol || '', fontSize: 11, fontWeight: 600, fill: '#fff', textAlign: 'center' }
        })
      }
      if (props.showLabels) drawLabel(group, cfg.label, h / 2 + 18)
      return keyShape
    }
  }, 'rect')

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
          stroke: themePalette.value.nodeStroke,
          lineWidth: isEmphasis ? 3 : 2
        }
      })
      group.addShape('text', {
        attrs: { x: 0, y: 3, text: cfg.symbol || 'T', fontSize: 11, fontWeight: 600, fill: '#fff', textAlign: 'center' }
      })
      if (props.showLabels) drawLabel(group, cfg.label, s / 2 + 22)
      return keyShape
    }
  }, 'polygon')

  nodesRegistered = true
}

function getLayoutConfig(): any {
  switch (props.layout) {
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
        alphaDecay: 0.05
      }
    case 'circular':
      return { type: 'circular' }
    default:
      return { type: 'dagre' }
  }
}

function getShapeType(nodeType: TopologyNodeType): string {
  const shape = nodeTypeMeta[nodeType].shape
  return shape === 'circle' ? 'device-circle'
    : shape === 'diamond' ? 'device-diamond'
    : 'device-rect'
}

function initGraph() {
  if (!graphEl.value) return
  ensureNodesRegistered()
  const { clientWidth: width, clientHeight: height } = graphEl.value

  graph = new (G6 as any).Graph({
    container: graphEl.value,
    width,
    height,
    fitView: true,
    fitViewPadding: props.fitViewPadding,
    animate: false,
    animateCfg: { duration: 0 },
    pixelRatio: 1,
    background: themePalette.value.canvasBg, // 'transparent'
    defaultNode: {
      type: 'device-circle',
      size: 32,
      style: { cursor: 'pointer' }
    },
    defaultEdge: {
      type: 'line',
      style: { stroke: themePalette.value.edgeDefault, lineWidth: 1.2, cursor: 'pointer' },
      labelCfg: { style: { fontSize: 10 } }
    },
    modes: {
      default: props.readonly
        ? ['drag-canvas', 'zoom-canvas']
        : ['drag-canvas', 'zoom-canvas', 'drag-node']
    },
    layout: getLayoutConfig()
  })

  graph.on('node:click', (evt: any) => {
    emit('node-click', evt.item.getModel() as TopologyGraphNode)
  })

  graph.on('node:dblclick', (evt: any) => {
    emit('node-double-click', evt.item.getModel() as TopologyGraphNode)
  })

  graph.on('edge:mouseenter', (evt: any) => {
    const model = evt.item.getModel()
    graph?.updateItem(evt.item, { label: model.label })
    emit('edge-mouseenter', model as TopologyGraphEdge)
  })

  graph.on('edge:mouseleave', (evt: any) => {
    graph?.updateItem(evt.item, { label: '' })
  })

  emit('ready')
}

function renderGraph() {
  if (!graph || !graphEl.value) return
  const palette = themePalette.value
  const data = {
    nodes: props.nodes.map(n => ({
      ...n,
      label: n.name,
      symbol: nodeTypeMeta[n.type].symbol,
      type: getShapeType(n.type)
    })),
    edges: props.edges.map(e => ({
      ...e,
      label: '',
      style: {
        stroke: statusColor[e.status].stroke,
        lineWidth: e.type === 'tie' ? 1 : (e.type === 'bus' ? 2 : 1.5),
        lineDash: e.type === 'tie' ? [4, 4] : undefined,
        opacity: 0.85
      }
    }))
  }
  // 关键:每次重渲染前,更新 G6 画布背景与默认色,适配主题
  if (graph.setBackground) graph.setBackground(palette.canvasBg)
  graph.changeData(data)
  graph.fitView(props.fitViewPadding)
}

function refreshLayout() {
  if (!graph) return
  graph.changeLayout(getLayoutConfig())
  graph.fitView(props.fitViewPadding)
}

// ============= 公开方法 =============
function zoomIn() { if (graph) graph.zoomTo(graph.getZoom() * 1.2) }
function zoomOut() { if (graph) graph.zoomTo(graph.getZoom() / 1.2) }
function fitView() { if (graph) graph.fitView(props.fitViewPadding) }
function focusNode(nodeId: string) {
  if (!graph) return
  const node = graph.findById(nodeId)
  if (!node) return
  const model = node.getModel()
  graph.setCenter([model.x, model.y])
  graph.setZoom(1.5)
}
function highlightNode(nodeId: string) {
  if (!graph) return
  graph.getNodes().forEach((n: any) => {
    const model = n.getModel()
    const isTarget = model.id === nodeId
    graph.updateItem(n, { opacity: isTarget ? 1 : 0.3 })
  })
}
function clearHighlight() {
  if (!graph) return
  graph.getNodes().forEach((n: any) => graph.updateItem(n, { opacity: 1 }))
}

defineExpose({ zoomIn, zoomOut, fitView, focusNode, highlightNode, clearHighlight, refreshLayout })

// ============= 生命周期 =============
function handleResize() {
  if (!graph || !graphEl.value) return
  graph.changeSize(graphEl.value.clientWidth, graphEl.value.clientHeight)
  graph.fitView(props.fitViewPadding)
}

watch(() => [props.nodes, props.edges], () => renderGraph(), { deep: true })
watch(() => props.layout, () => refreshLayout())

onMounted(async () => {
  await nextTick()
  initGraph()
  renderGraph()
  window.addEventListener('resize', handleResize)
  observeTheme()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  themeObserver?.disconnect()
  if (graph) {
    graph.destroy()
    graph = null
  }
})
</script>

<template>
  <div ref="graphEl" class="topology-graph" :style="{ height: props.height }"></div>
</template>

<style scoped>
.topology-graph {
  width: 100%;
  /* 透明背景,让父容器(卡片)的底色穿透 — 亮色卡片白、暗色卡片深 */
  background: transparent;
}
</style>