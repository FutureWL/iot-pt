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
// 布局预设:
//   dagre    - 通用有向图布局(TB,流程图风格,默认)
//   dagre-lr - 电网一次接线图风格(LR + 显式 level rank + 母线贯穿)
//   force    - 力导向(适合无层级关系的小图)
//   circular - 环形
export type LayoutType = 'dagre' | 'dagre-lr' | 'force' | 'circular'

export interface TopologyGraphNode {
  id: string
  name: string
  type: TopologyNodeType
  voltageLevel: VoltageLevel
  status: NodeStatus
  region?: string
  deviceId?: number
  substationCode?: string
  /**
   * 可选:布局 rank。dagre-lr 会按此字段把节点分到不同的水平列。
   *   - 不传:回退到自动 dagre(按边方向推 rank,环会错乱)
   *   - 传数字:严格按数值分层,适合有环(联络线)的电网拓扑
   * 建议值: 0 = 进线电源, 1 = 主变, 2 = 母线, 3 = 出线开关,
   *         4 = 环网柜, 5 = 末端设备(分接箱/用户)
   */
  level?: number
  /** 母线专属:水平贯穿宽度,默认 200 */
  width?: number
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
// 画法参照国标 GB/T 4728(电气简图用图形符号):
//   - 母线: 水平贯穿的粗线
//   - 变压器: 两个相交圆(同心圆也可,这里取行业最常见画法)
//   - 开关/断路器: 矩形 + 中间斜杠
//   - 环网柜: 矩形 + 内部文字
//   - 变电站: 双层矩形
//   - 分接箱: 实心小圆点
//   - 用户表: 圆 + 文字
let nodesRegistered = false
function ensureNodesRegistered() {
  if (nodesRegistered) return

  // 节点下方的文字标签。位置由 nodeConfig.labelPosition 控制:
  //   'below'(默认) / 'above' / 'inside'
  function drawLabel(group: any, name: string, yOffset: number, position: string = 'below') {
    const text = name || ''
    // 动态算宽度: 中文字符 12px, ASCII 6.5px
    const w = Math.max(48, calcTextWidth(text, 13) + 14)
    const palette = themePalette.value

    if (position === 'inside') {
      group.addShape('text', {
        attrs: {
          x: 0, y: yOffset,
          text,
          fontSize: 12,
          fill: '#fff',
          fontWeight: 600,
          textAlign: 'center',
          textBaseline: 'middle'
        }
      })
      return
    }

    const labelY = position === 'above' ? yOffset - 8 : yOffset
    group.addShape('rect', {
      attrs: {
        x: -w / 2, y: labelY - 9,
        width: w, height: 18,
        fill: palette.labelBg,
        stroke: palette.labelBorder,
        lineWidth: 1,
        radius: 3
      }
    })
    group.addShape('text', {
      attrs: {
        x: 0, y: labelY + 4,
        text,
        fontSize: 12,
        fill: palette.labelText,
        fontWeight: 500,
        textAlign: 'center'
      }
    })
  }

  // 中英文混合文本宽度估算
  function calcTextWidth(text: string, fontSize: number): number {
    let width = 0
    for (const ch of text) {
      width += /[\u4e00-\u9fff\u3000-\u303f\uff00-\uffef]/.test(ch) ? fontSize : fontSize * 0.55
    }
    return Math.ceil(width)
  }

  // ============= 圆形节点: switch / ring_main / junction / meter =============
  G6.registerNode('device-circle', {
    draw(cfg: any, group: any) {
      const status = cfg.status as NodeStatus
      const color = statusColor[status]
      const isEmphasis = status === 'fault' || status === 'warning'
      const isJunction = cfg.type === 'junction'
      const isMeter = cfg.type === 'meter'
      const isSwitch = cfg.type === 'switch'
      const r = isJunction ? 7 : isMeter ? 14 : 14
      const keyShape = group.addShape('circle', {
        attrs: {
          x: 0, y: 0, r,
          fill: color.fill,
          stroke: themePalette.value.nodeStroke,
          lineWidth: isEmphasis ? 3 : 2
        }
      })
      // 分接箱不画内文(太小)
      if (!isJunction) {
        group.addShape('text', {
          attrs: { x: 0, y: 3, text: cfg.symbol || '·', fontSize: 11, fontWeight: 600, fill: '#fff', textAlign: 'center' }
        })
      }
      if (props.showLabels) drawLabel(group, cfg.name, r + 16)
      return keyShape
    }
  }, 'circle')

  // ============= 矩形节点: ring_main(环网柜) / switch(开关矩形化) =============
  G6.registerNode('device-rect', {
    draw(cfg: any, group: any) {
      const status = cfg.status as NodeStatus
      const color = statusColor[status]
      const isEmphasis = status === 'fault' || status === 'warning'
      const isRingMain = cfg.type === 'ring_main'
      const isSwitch = cfg.type === 'switch'
      const w = isSwitch ? 14 : isRingMain ? 36 : 40
      const h = isSwitch ? 10 : isRingMain ? 24 : 30
      const keyShape = group.addShape('rect', {
        attrs: {
          x: -w / 2, y: -h / 2, w, h,
          fill: color.fill,
          stroke: themePalette.value.nodeStroke,
          lineWidth: isEmphasis ? 3 : 2,
          radius: 2
        }
      })
      if (isSwitch) {
        // 开关内部画一道斜杠,表示断路器符号
        group.addShape('line', {
          attrs: { x1: -w / 2 + 1, y1: h / 2 - 1, x2: w / 2 - 1, y2: -h / 2 + 1, stroke: '#fff', lineWidth: 1.5 }
        })
      } else {
        group.addShape('text', {
          attrs: { x: 0, y: 3, text: cfg.symbol || '', fontSize: 11, fontWeight: 600, fill: '#fff', textAlign: 'center' }
        })
      }
      if (props.showLabels) drawLabel(group, cfg.name, h / 2 + 16)
      return keyShape
    }
  }, 'rect')

  // ============= 母线: 水平贯穿的粗线 =============
  // width 由节点的 cfg.width 控制(用户可指定,默认 200)
  G6.registerNode('busbar', {
    draw(cfg: any, group: any) {
      const status = cfg.status as NodeStatus
      const color = statusColor[status]
      const w = Number(cfg.width) || 200
      const h = 8
      const keyShape = group.addShape('rect', {
        attrs: {
          x: -w / 2, y: -h / 2, w, h,
          fill: color.stroke,           // 母线用深色,与节点语义色不同
          stroke: color.stroke,
          lineWidth: 1,
          radius: 1
        }
      })
      // 母线上方标注电压等级(简洁的小标签)
      if (cfg.voltageLevel) {
        group.addShape('text', {
          attrs: {
            x: 0, y: -h / 2 - 12,
            text: cfg.voltageLevel,
            fontSize: 12,
            fontWeight: 600,
            fill: color.stroke,
            textAlign: 'center'
          }
        })
      }
      if (props.showLabels) drawLabel(group, cfg.name, h / 2 + 14)
      return keyShape
    }
  }, 'rect')

  // ============= 变电站: 双层矩形 =============
  G6.registerNode('substation', {
    draw(cfg: any, group: any) {
      const status = cfg.status as NodeStatus
      const color = statusColor[status]
      const isEmphasis = status === 'fault' || status === 'warning'
      const w = 60, h = 36
      // 外框
      const keyShape = group.addShape('rect', {
        attrs: {
          x: -w / 2, y: -h / 2, w, h,
          fill: color.fill,
          stroke: themePalette.value.nodeStroke,
          lineWidth: isEmphasis ? 3 : 2,
          radius: 3
        }
      })
      // 内框(双层矩形表示变电站)
      group.addShape('rect', {
        attrs: {
          x: -w / 2 + 5, y: -h / 2 + 5,
          w: w - 10, h: h - 10,
          fill: 'transparent',
          stroke: '#fff',
          lineWidth: 1.2,
          radius: 2
        }
      })
      group.addShape('text', {
        attrs: {
          x: 0, y: 3,
          text: cfg.symbol || '⚡',
          fontSize: 14,
          fontWeight: 700,
          fill: '#fff',
          textAlign: 'center'
        }
      })
      if (props.showLabels) drawLabel(group, cfg.name, h / 2 + 16)
      return keyShape
    }
  }, 'rect')

  // ============= 变压器: 两个相交圆(国标 GB/T 4728) =============
  G6.registerNode('transformer', {
    draw(cfg: any, group: any) {
      const status = cfg.status as NodeStatus
      const color = statusColor[status]
      const isEmphasis = status === 'fault' || status === 'warning'
      const r = 12
      // 圆心相距 r(相切),国标典型画法
      const leftCircle = group.addShape('circle', {
        attrs: {
          x: -r / 2, y: 0, r,
          fill: 'transparent',
          stroke: color.fill,
          lineWidth: isEmphasis ? 3 : 2
        }
      })
      const rightCircle = group.addShape('circle', {
        attrs: {
          x: r / 2, y: 0, r,
          fill: 'transparent',
          stroke: color.fill,
          lineWidth: isEmphasis ? 3 : 2
        }
      })
      // 内嵌 Y 字符(变压器 symbol)
      group.addShape('text', {
        attrs: {
          x: 0, y: 3,
          text: cfg.symbol || 'T',
          fontSize: 11,
          fontWeight: 600,
          fill: color.fill,
          textAlign: 'center'
        }
      })
      if (props.showLabels) drawLabel(group, cfg.name, r + 18)
      return leftCircle  // 用左圆作为 keyShape
    }
  }, 'circle')

  // ============= 自定义边: tie-edge(联络线,虚线 + 橙色) =============
  G6.registerEdge('tie-edge', {
    draw(cfg: any, group: any) {
      const status = (cfg.status || 'normal') as NodeStatus
      const color = statusColor[status]
      const path = group.addShape('path', {
        attrs: {
          path: [['M', cfg.startPoint.x, cfg.startPoint.y],
                 ['L', cfg.endPoint.x, cfg.endPoint.y]],
          stroke: '#e6a23c',               // 橙色突出
          lineWidth: 1.8,
          lineDash: [6, 4],
          opacity: 0.9,
          cursor: 'pointer'
        }
      })
      // 端点小箭头(国标:开放三角)
      if (cfg.endPoint) {
        const angle = Math.atan2(cfg.endPoint.y - cfg.startPoint.y,
                                  cfg.endPoint.x - cfg.startPoint.x)
        const arrowSize = 6
        group.addShape('path', {
          attrs: {
            path: [['M', cfg.endPoint.x - arrowSize * Math.cos(angle - Math.PI / 7),
                          cfg.endPoint.y - arrowSize * Math.sin(angle - Math.PI / 7)],
                   ['L', cfg.endPoint.x, cfg.endPoint.y],
                   ['L', cfg.endPoint.x - arrowSize * Math.cos(angle + Math.PI / 7),
                          cfg.endPoint.y - arrowSize * Math.sin(angle + Math.PI / 7)]],
            stroke: '#e6a23c',
            lineWidth: 1.5
          }
        })
      }
      return path
    }
  }, 'line')

  nodesRegistered = true
}

function getLayoutConfig(): any {
  switch (props.layout) {
    case 'dagre':
      // 通用有向图(流程图风格)
      return {
        type: 'dagre',
        rankdir: 'TB',
        align: 'UL',
        nodesepFunc: (d: any) => (d.type === 'substation' ? 50 : 28),
        ranksepFunc: (d: any) => (d.type === 'substation' ? 80 : 50),
        preventOverlap: true
      }
    case 'dagre-lr':
      // 电网一次接线图风格:
      //   LR(从左到右)对应高压→低压
      //   rank 函数读 node.level 显式分层,避免环(联络线)错乱
      //   间距加大容纳母线水平贯穿
      return {
        type: 'dagre',
        rankdir: 'LR',
        align: 'UL',
        nodesep: 60,
        ranksep: 110,
        preventOverlap: true,
        nodeSize: 40,
        rank: (node: any) => (typeof node.level === 'number' ? node.level : undefined)
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
  // 按 type 直接映射到自定义节点类型
  switch (nodeType) {
    case 'substation':  return 'substation'
    case 'transformer': return 'transformer'
    case 'busbar':      return 'busbar'
    case 'switch':      return 'device-rect'
    case 'ring_main':   return 'device-rect'
    case 'junction':    return 'device-circle'
    case 'meter':       return 'device-circle'
  }
}

function getEdgeType(edgeType: EdgeType): string {
  return edgeType === 'tie' ? 'tie-edge' : 'line'
}

function initGraph() {
  if (!graphEl.value) return
  ensureNodesRegistered()
  const { clientWidth: width, clientHeight: height } = graphEl.value
  // 高 DPR 屏幕(视网膜)上必须用 devicePixelRatio 才能清晰
  const dpr = (typeof window !== 'undefined' && window.devicePixelRatio) || 1

  graph = new (G6 as any).Graph({
    container: graphEl.value,
    width,
    height,
    fitView: true,
    fitViewPadding: props.fitViewPadding,
    animate: false,
    animateCfg: { duration: 0 },
    pixelRatio: dpr,
    background: themePalette.value.canvasBg, // 'transparent'
    defaultNode: {
      type: 'device-circle',
      size: 32,
      style: { cursor: 'pointer' }
    },
    defaultEdge: {
      type: 'line',
      style: { stroke: themePalette.value.edgeDefault, lineWidth: 1.4, cursor: 'pointer' },
      labelCfg: { style: { fontSize: 12 } }
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
      // 不传 label 字段!G6 内置 rect/circle/polygon 会根据 cfg.label 自动
      // 在节点中心画一遍名字,与我们的 drawLabel 形成双重渲染。
      // 名字完全交给 drawLabel 处理(读 cfg.name)。
      symbol: nodeTypeMeta[n.type].symbol,
      type: getShapeType(n.type)
    })),
    edges: props.edges.map(e => ({
      ...e,
      label: '',
      type: getEdgeType(e.type),         // tie -> 'tie-edge', 其他 -> 'line'
      style: {
        stroke: statusColor[e.status].stroke,
        lineWidth: e.type === 'bus' ? 2.2 : 1.5,
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
  <div
    ref="graphEl"
    class="topology-graph"
    :style="{ height: props.height }"
  />
</template>

<style scoped>
.topology-graph {
  width: 100%;
  /* 透明背景,让父容器(卡片)的底色穿透 — 亮色卡片白、暗色卡片深 */
  background: transparent;
}
</style>