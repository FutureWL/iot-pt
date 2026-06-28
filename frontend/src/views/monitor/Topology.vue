<script setup lang="ts">
/**
 * 电网拓扑 · 维护页面
 *
 * 这是拓扑维护入口(后续会支持增删改节点、拖拽保存布局)。
 * 现在先把 G6 渲染逻辑迁到 <TopologyGraph> 共享组件。
 *
 * 与 /dashboard 的区别:
 * - /dashboard: 只读浏览,围绕拓扑展示 KPI + 告警上下文
 * - /monitor/topology (本页): 拓扑结构维护,后续加编辑功能
 */
import { ref, onMounted, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  Warning, Refresh, FullScreen, Aim, Plus, Minus, SetUp
} from '@element-plus/icons-vue'
import TopologyGraph, {
  type TopologyGraphNode,
  type TopologyGraphEdge,
  type NodeStatus,
  type TopologyNodeType
} from '@/components/TopologyGraph.vue'
import {
  getTopologyGraph,
  listTopologyRegions,
  type TopologyGraph as TopologyGraphData,
  type TopologyRegionVO
} from '@/api/monitor/topology'

const router = useRouter()
const loading = ref(false)
const regions = ref<TopologyRegionVO[]>([])
const activeRegionId = ref<string>('')
const layoutType = ref<'dagre' | 'force' | 'circular'>('dagre')

const graphData = ref<TopologyGraphData | null>(null)
const selectedNode = ref<TopologyGraphNode | null>(null)

// ============= Mock 数据 =============
function buildMockGraph(regionId?: string): TopologyGraphData {
  const nodes: TopologyGraphNode[] = [
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

// ============= 状态色板(给图例和右侧详情用) =============
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

// ============= 数据加载 =============
async function loadRegions() {
  try {
    const res: any = await listTopologyRegions()
    regions.value = res.data ?? []
  } catch {
    regions.value = [
      { id: '北京-朝阳', name: '北京·朝阳供电区', nodeCount: 13, faultCount: 1 }
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
}

// ============= G6 实例引用 =============
const graphRef = ref<InstanceType<typeof TopologyGraph> | null>(null)

function onZoomIn() { graphRef.value?.zoomIn() }
function onZoomOut() { graphRef.value?.zoomOut() }
function onFitView() { graphRef.value?.fitView() }

function onFullScreen() {
  const el = document.querySelector('.center-panel') as HTMLElement | null
  if (!el) return
  if (document.fullscreenElement) document.exitFullscreen()
  else el.requestFullscreen()
}

function onNodeClick(node: TopologyGraphNode) {
  selectedNode.value = node
}

function onNodeDoubleClick(node: TopologyGraphNode) {
  // 双击节点:跳转到该节点关联的设备详情(后续可改为打开编辑对话框)
  if (node.deviceId) router.push(`/device/list?deviceId=${node.deviceId}`)
}

function goDeviceDetail(node: TopologyGraphNode) {
  if (node.deviceId) router.push(`/device/list?deviceId=${node.deviceId}`)
}

const stats = computed(() => graphData.value?.stats ?? { nodeCount: 0, edgeCount: 0, faultCount: 0, warningCount: 0 })

watch(activeRegionId, loadTopology)

onMounted(loadRegions)
</script>

<template>
  <div
    v-loading="loading"
    class="page-container topo-page"
  >
    <div class="page-header">
      <h2 class="page-title">
        电网拓扑 · 维护
      </h2>
      <div class="header-tools">
        <el-select
          v-model="activeRegionId"
          placeholder="选择区域"
          style="width: 200px"
        >
          <el-option
            v-for="r in regions"
            :key="r.id"
            :label="`${r.name} (${r.nodeCount})`"
            :value="r.id"
          />
        </el-select>
        <el-radio-group
          v-model="layoutType"
          size="default"
        >
          <el-radio-button value="dagre">
            层级
          </el-radio-button>
          <el-radio-button value="force">
            力导向
          </el-radio-button>
          <el-radio-button value="circular">
            环形
          </el-radio-button>
        </el-radio-group>
        <el-button
          :icon="Refresh"
          @click="loadTopology"
        >
          刷新
        </el-button>
        <el-button
          type="primary"
          :icon="SetUp"
        >
          编辑模式
        </el-button>
      </div>
    </div>

    <!-- 统计卡 -->
    <el-row
      :gutter="12"
      class="mb-12"
    >
      <el-col
        :xs="6"
        :sm="6"
      >
        <div class="stat-mini">
          <div class="stat-mini-num">
            {{ stats.nodeCount }}
          </div><div class="stat-mini-label">
            节点数
          </div>
        </div>
      </el-col>
      <el-col
        :xs="6"
        :sm="6"
      >
        <div class="stat-mini">
          <div class="stat-mini-num">
            {{ stats.edgeCount }}
          </div><div class="stat-mini-label">
            连接数
          </div>
        </div>
      </el-col>
      <el-col
        :xs="6"
        :sm="6"
      >
        <div
          class="stat-mini"
          :class="{ alert: stats.warningCount > 0 }"
        >
          <div class="stat-mini-num">
            {{ stats.warningCount }}
          </div><div class="stat-mini-label">
            告警
          </div>
        </div>
      </el-col>
      <el-col
        :xs="6"
        :sm="6"
      >
        <div
          class="stat-mini"
          :class="{ danger: stats.faultCount > 0 }"
        >
          <div class="stat-mini-num">
            {{ stats.faultCount }}
          </div><div class="stat-mini-label">
            故障
          </div>
        </div>
      </el-col>
    </el-row>

    <div class="topo-layout">
      <!-- 左:图例 + 节点列表 -->
      <div class="page-card left-panel">
        <h3 class="card-title">
          设备类型
        </h3>
        <div class="legend-list">
          <div
            v-for="(m, t) in nodeTypeMeta"
            :key="t"
            class="legend-item"
          >
            <span
              class="legend-shape"
              :class="`shape-${m.shape}`"
              :style="{ background: m.color }"
            >{{ m.symbol }}</span>
            <span class="legend-label">{{ m.label }}</span>
          </div>
        </div>

        <h3 class="card-title mt-16">
          节点列表 ({{ graphData?.nodes.length ?? 0 }})
        </h3>
        <div class="node-list">
          <div
            v-for="n in graphData?.nodes"
            :key="n.id"
            class="node-item"
            :class="{ active: selectedNode?.id === n.id }"
            @click="onNodeClick(n)"
          >
            <span
              class="status-dot"
              :style="{ background: statusColor[n.status].fill }"
            />
            <div class="node-info">
              <div class="node-name">
                {{ n.name }}
              </div>
              <div class="node-meta">
                <el-tag
                  size="small"
                  effect="plain"
                >
                  {{ nodeTypeMeta[n.type].label }}
                </el-tag>
                <span class="voltage">{{ n.voltageLevel }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 中:画布(用共享组件) -->
      <div class="page-card center-panel">
        <TopologyGraph
          ref="graphRef"
          :nodes="graphData?.nodes ?? []"
          :edges="graphData?.edges ?? []"
          :layout="layoutType"
          :readonly="true"
          height="100%"
          @node-click="onNodeClick"
          @node-double-click="onNodeDoubleClick"
        />

        <div class="canvas-tools">
          <el-button
            :icon="Plus"
            size="small"
            circle
            title="放大"
            @click="onZoomIn"
          />
          <el-button
            :icon="Minus"
            size="small"
            circle
            title="缩小"
            @click="onZoomOut"
          />
          <el-button
            :icon="Aim"
            size="small"
            circle
            title="自适应"
            @click="onFitView"
          />
          <el-button
            :icon="FullScreen"
            size="small"
            circle
            title="全屏"
            @click="onFullScreen"
          />
        </div>

        <div class="status-legend">
          <div class="legend-row">
            <span class="legend-item-mini"><span class="dot normal" />正常</span>
            <span class="legend-item-mini"><span class="dot warning" />警告</span>
            <span class="legend-item-mini"><span class="dot fault" />故障</span>
            <span class="legend-item-mini"><span class="dot offline" />离线</span>
          </div>
          <div class="legend-row">
            <span class="legend-item-mini"><span class="line-solid" />实线:馈线/母线</span>
            <span class="legend-item-mini"><span class="line-dashed" />虚线:联络线</span>
            <span class="legend-item-mini legend-tip">双击节点 → 关联设备</span>
          </div>
        </div>
      </div>

      <!-- 右:详情 -->
      <div class="page-card right-panel">
        <template v-if="selectedNode">
          <h3 class="card-title">
            {{ selectedNode.name }}
          </h3>
          <el-descriptions
            :column="1"
            border
            size="small"
          >
            <el-descriptions-item label="节点 ID">
              <code>{{ selectedNode.id }}</code>
            </el-descriptions-item>
            <el-descriptions-item label="设备类型">
              <el-tag size="small">
                {{ nodeTypeMeta[selectedNode.type].label }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="电压等级">
              <el-tag
                size="small"
                type="warning"
              >
                {{ selectedNode.voltageLevel }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="运行状态">
              <el-tag
                :type="statusColor[selectedNode.status].tag as any"
                size="small"
              >
                {{ statusColor[selectedNode.status].label }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="所在区域">
              {{ selectedNode.region || '—' }}
            </el-descriptions-item>
            <el-descriptions-item
              v-if="selectedNode.deviceId"
              label="关联设备"
            >
              <el-link
                type="primary"
                :underline="false"
                @click="goDeviceDetail(selectedNode)"
              >
                设备 #{{ selectedNode.deviceId }} →
              </el-link>
            </el-descriptions-item>
          </el-descriptions>

          <div class="alert-zone">
            <h4 class="sub-title">
              维护操作
            </h4>
            <el-button-group>
              <el-button
                :icon="SetUp"
                size="small"
              >
                编辑属性
              </el-button>
              <el-button
                :icon="Plus"
                size="small"
              >
                新增连接
              </el-button>
            </el-button-group>
            <el-alert
              type="info"
              :closable="false"
              show-icon
              class="mt-8"
            >
              双击节点可跳转到关联设备;后续将支持拖拽节点重新布局 / 拖拽创建新连线。
            </el-alert>
          </div>
        </template>
        <el-empty
          v-else
          description="点击节点查看详情 / 双击进入编辑"
          :image-size="80"
        />
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
.mt-8 { margin-top: $spacing-8; }
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
  border-radius: $radius-small; border: 2px solid #fff; box-shadow: var(--iot-shadow-light);
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
  padding: $spacing-8; border-radius: $radius-base; cursor: pointer;
  border: 1px solid transparent; margin-bottom: $spacing-4;
  transition: background $transition-fast;
  &:hover { background: var(--iot-bg-hover); }
  &.active { background: var(--iot-color-primary-light-9); border-color: var(--iot-color-primary-light-5); }
}
.status-dot { width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0; }
.node-info { flex: 1; min-width: 0; }
.node-name { font-size: $font-size-small; font-weight: $font-weight-medium; }
.node-meta { display: flex; align-items: center; gap: $spacing-4; margin-top: 2px; }
.voltage { font-size: $font-size-extra-small; color: var(--iot-text-secondary); font-family: var(--iot-font-family-code); }

.center-panel { padding: 0; position: relative; overflow: hidden; }
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