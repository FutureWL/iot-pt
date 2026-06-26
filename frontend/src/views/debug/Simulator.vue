<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Position, Connection, VideoPlay, VideoPause, Delete, View, DataLine, EditPen } from '@element-plus/icons-vue'
// @ts-ignore
import mqtt from 'mqtt'
import { allProducts, type IotProductVO } from '@/api/iot/product'
import { pageDevices, getDevice, type IotDeviceVO } from '@/api/iot/device'
import { getRealtime, getHistory, type RealtimeDevice } from '@/api/data/realtime'
import { simulateTcp, generateMockData, type SimulateRequest, type SimulateResponse } from '@/api/debug/simulator'
import { WSClient } from '@/utils/ws'

// ========== 基础数据 ==========
const products = ref<IotProductVO[]>([])
const devices = ref<IotDeviceVO[]>([])

// ========== 配置区 ==========
const config = reactive({
  productId: undefined as number | undefined,
  deviceId: undefined as number | undefined,
  protocol: 'MQTT' as 'MQTT' | 'TCP',
  brokerUrl: 'ws://localhost:33407/mqtt',
  tcpHost: 'localhost',
  tcpPort: 33410,
  topic: 'iot/{productKey}/{deviceKey}/property/post',
  secret: ''   // 自动从 /full 拿
})

// ========== 属性模板生成器 ==========
const generators = ref<Array<{ identifier: string; type: 'int'|'float'|'bool'|'string'; mode: 'random'|'inc'|'const'; min: number; max: number; value: any; unit: string }>>([
  { identifier: 'temperature', type: 'float', mode: 'random', min: 20, max: 35, value: 0, unit: '℃' },
  { identifier: 'humidity',    type: 'float', mode: 'random', min: 40, max: 80, value: 0, unit: '%' },
  { identifier: 'battery',     type: 'int',   mode: 'inc',    min: 0,  max: 100, value: 100, unit: '%' }
])

function addGenerator() {
  generators.value.push({ identifier: 'new_prop', type: 'float', mode: 'random', min: 0, max: 100, value: 0, unit: '' })
}
function removeGenerator(idx: number) {
  generators.value.splice(idx, 1)
}

// 序列号,inc 模式时递增
let seq = 0

// ========== 模拟数据生成(前端实现) ==========
function generateData() {
  const data: Record<string, any> = {}
  for (const g of generators.value) {
    switch (g.type) {
      case 'int': {
        let v = 0
        if (g.mode === 'inc') v = (Number(g.value) || 0) + seq
        else if (g.mode === 'const') v = Number(g.value) || 0
        else v = Math.floor(Math.random() * (g.max - g.min + 1)) + g.min
        data[g.identifier] = v
        break
      }
      case 'float': {
        let v = 0
        if (g.mode === 'inc') v = (Number(g.value) || 0) + seq * 0.5
        else if (g.mode === 'const') v = Number(g.value) || 0
        else v = Math.random() * (g.max - g.min) + g.min
        data[g.identifier] = Math.round(v * 100) / 100
        break
      }
      case 'bool':
        data[g.identifier] = g.mode === 'const' ? Boolean(g.value) : Math.random() > 0.5
        break
      case 'string':
        data[g.identifier] = g.mode === 'const' ? String(g.value) : `value-${seq}`
        break
    }
  }
  return data
}

// ========== 选设备后自动填 secret + topic ==========
const selectedDevice = ref<IotDeviceVO | null>(null)
async function onDeviceChange() {
  if (!config.deviceId) return
  const d = devices.value.find(x => x.id === config.deviceId)
  if (d) {
    selectedDevice.value = d
    // 拉完整 secret
    try {
      const res: any = await getDevice(d.id, true)
      config.secret = res.data.deviceSecret
    } catch (e) { /* ignore */ }
  }
}

const renderedTopic = computed(() => {
  if (!selectedDevice.value) return config.topic
  const pk = products.value.find(p => p.id === selectedDevice.value?.productId)?.productKey || '{productKey}'
  const dk = selectedDevice.value.deviceKey
  return config.topic
    .replace('{productKey}', pk)
    .replace('{deviceKey}', dk)
})

// ========== 日志区 ==========
interface LogEntry {
  ts: number
  level: 'send' | 'recv' | 'ok' | 'err' | 'info'
  text: string
}
const logs = ref<LogEntry[]>([])
const MAX_LOG = 200

function pushLog(level: LogEntry['level'], text: string) {
  logs.value.unshift({ ts: Date.now(), level, text })
  if (logs.value.length > MAX_LOG) logs.value.splice(MAX_LOG)
}
function clearLogs() { logs.value = [] }

// ========== 实时影子 / 时序 ==========
const liveDevices = ref<RealtimeDevice[]>([])
const liveSelectedProps = ref<RealtimeDevice['properties']>([])

async function refreshLive() {
  try {
    const res: any = await getRealtime()
    liveDevices.value = res.data ?? []
    // 当前选中设备的属性
    if (config.deviceId) {
      const dev = liveDevices.value.find(d => d.deviceId === config.deviceId)
      liveSelectedProps.value = dev?.properties ?? []
    }
  } catch {}
}

// ========== 时序图 ==========
const chartEl = ref<HTMLDivElement>()
let chart: any = null
const histData = ref<{ ts: string; value: string }[]>([])

async function refreshHistory() {
  if (!config.deviceId) return
  const prop = liveSelectedProps.value.find(p => p.identifier === 'temperature')
  if (!prop) return
  try {
    const end = Date.now()
    const start = end - 5 * 60_000  // 最近 5 分钟
    const res: any = await getHistory(config.deviceId, 'temperature', 'double', start, end)
    histData.value = res.data ?? []
    drawChart()
  } catch {}
}

function drawChart() {
  if (!chartEl.value) return
  if (!chart) {
    // @ts-ignore
    import('echarts').then((ec: any) => {
      chart = ec.init(chartEl.value)
      drawChart()
    })
    return
  }
  const data = histData.value
    .map(p => [Number(p.ts), Number(p.value)])
    .filter(([_, v]) => !isNaN(v))
    .sort((a, b) => a[0] - b[0])
  chart.setOption({
    grid: { left: 50, right: 20, top: 20, bottom: 30 },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'time' },
    yAxis: { type: 'value' },
    series: [{
      name: '温度', type: 'line', smooth: true, showSymbol: false,
      itemStyle: { color: '#409eff' },
      areaStyle: { color: 'rgba(64,158,255,0.2)' },
      data
    }]
  })
}

// ========== MQTT 客户端 ==========
let mqttClient: any = null
const mqttStatus = ref<'disconnected' | 'connecting' | 'connected'>('disconnected')

function connectMqtt() {
  if (!config.deviceId) { ElMessage.warning('请先选择设备'); return }
  if (!config.secret) { ElMessage.warning('缺少设备密钥'); return }
  disconnectMqtt()
  mqttStatus.value = 'connecting'
  pushLog('info', `[MQTT] 连接 ${config.brokerUrl}`)
  try {
    mqttClient = mqtt.connect(config.brokerUrl, {
      clientId: `sim-${config.deviceId}-${Date.now()}`,
      clean: true,
      connectTimeout: 4000
    })
    mqttClient.on('connect', () => {
      mqttStatus.value = 'connected'
      pushLog('ok', '[MQTT] 已连接 broker')
    })
    mqttClient.on('error', (err: any) => {
      mqttStatus.value = 'disconnected'
      pushLog('err', `[MQTT] 连接失败: ${err.message || err}`)
    })
    mqttClient.on('close', () => {
      mqttStatus.value = 'disconnected'
      pushLog('info', '[MQTT] 连接已关闭')
    })
  } catch (e: any) {
    mqttStatus.value = 'disconnected'
    pushLog('err', `[MQTT] 异常: ${e.message}`)
  }
}

function disconnectMqtt() {
  if (mqttClient) {
    try { mqttClient.end(true) } catch {}
    mqttClient = null
  }
  mqttStatus.value = 'disconnected'
}

// ========== 发送一条 ==========
const sending = ref(false)
async function sendOne() {
  if (!config.deviceId) { ElMessage.warning('请先选择设备'); return }
  const data = generateData()
  seq++

  if (config.protocol === 'MQTT') {
    if (mqttStatus.value !== 'connected') { ElMessage.warning('MQTT 未连接'); return }
    const payload = JSON.stringify(data)
    mqttClient.publish(renderedTopic.value, payload, { qos: 0 })
    pushLog('send', `[MQTT] → ${renderedTopic.value}\n${JSON.stringify(data, null, 2)}`)
    ElMessage.success('已发送')
  } else {
    // TCP
    const product = products.value.find(p => p.id === selectedDevice.value?.productId)
    const req: SimulateRequest = {
      productKey: product?.productKey || '',
      deviceKey: selectedDevice.value?.deviceKey || '',
      secret: config.secret,
      type: 'property',
      data
    }
    pushLog('send', `[TCP] → localhost:${config.tcpPort}\n${JSON.stringify(data, null, 2)}`)
    sending.value = true
    try {
      const res: any = await simulateTcp(req)
      if (res.ok) {
        pushLog('recv', `[TCP] ← auth: ${JSON.stringify(res.auth)}\nack: ${JSON.stringify(res.ack)}`)
        ElMessage.success('已发送,服务端 ACK')
      } else {
        pushLog('err', `[TCP] 失败 stage=${res.stage} error=${res.error || res.response?.message}`)
        ElMessage.error(`TCP 失败: ${res.error || res.response?.message}`)
      }
    } catch (e: any) {
      pushLog('err', `[TCP] 异常: ${e.message}`)
    } finally {
      sending.value = false
    }
  }
  // 刷新实时 + 时序
  setTimeout(() => manualRefresh(), 300)
}

// ========== 连续发送 ==========
const looping = ref(false)
const loopInterval = ref(1000)  // ms
let loopTimer: any = null

function startLoop() {
  if (!config.deviceId) { ElMessage.warning('请先选择设备'); return }
  looping.value = true
  loopTimer = setInterval(() => {
    sendOne()
  }, loopInterval.value)
  pushLog('info', `[循环] 启动,每 ${loopInterval.value}ms 一条`)
}

function stopLoop() {
  if (loopTimer) clearInterval(loopTimer)
  loopTimer = null
  looping.value = false
  pushLog('info', '[循环] 停止')
}

// ========== 加载产品/设备列表 ==========
async function loadOptions() {
  const [pRes, dRes]: any[] = await Promise.all([allProducts(), pageDevices({ pageSize: 100, pageNum: 1 } as any)])
  products.value = pRes.data ?? []
  devices.value = dRes.data?.records ?? []
}

const filteredDevices = computed(() => {
  if (!config.productId) return devices.value
  const pk = products.value.find(p => p.id === config.productId)?.productKey
  return devices.value.filter(d => d.productKey === pk)
})

// ========== 快捷模板(从物模型拉) ==========
const thingModelProps = ref<{ identifier: string; name: string; type: string; unit: string }[]>([])

async function loadThingModel() {
  if (!config.productId) { thingModelProps.value = []; return }
  const p = products.value.find(x => x.id === config.productId)
  if (!p) return
  try {
    const { getProduct } = await import('@/api/iot/product')
    const res: any = await getProduct(p.id)
    const t = JSON.parse(res.data.thingModel || '{}')
    thingModelProps.value = (t.properties || []).map((x: any) => ({
      identifier: x.identifier, name: x.name, type: x.type, unit: x.unit || ''
    }))
  } catch {}
}

watch(() => config.productId, () => { loadThingModel(); config.deviceId = undefined })

function importFromModel() {
  for (const p of thingModelProps.value) {
    if (generators.value.find(g => g.identifier === p.identifier)) continue
    let mode: any = 'random'
    let min = 0, max = 100, value: any = 0
    if (p.type === 'bool') { mode = 'random'; max = 1 }
    else if (p.type === 'int') { mode = 'inc'; min = 0; max = 100; value = 100 }
    else if (p.type === 'float') { mode = 'random'; min = 0; max = 100 }
    generators.value.push({ identifier: p.identifier, type: p.type as any, mode, min, max, value, unit: p.unit })
  }
  ElMessage.success('已从物模型导入')
}

watch(() => config.deviceId, onDeviceChange)
watch(() => config.protocol, (p) => {
  if (p === 'MQTT') connectMqtt()
  else disconnectMqtt()
})

// ========== 手动刷新(不轮询) ==========
const lastRefresh = ref<number | null>(null)
async function manualRefresh() {
  await Promise.all([refreshLive(), refreshHistory()])
  lastRefresh.value = Date.now()
}

// ========== WebSocket 实时推送 ==========
const wsStatus = ref<'disconnected' | 'connected'>('disconnected')
let ws: WSClient | null = null

function handleShadowUpdate(evt: any) {
  // 只处理当前选中设备的事件
  if (!config.deviceId || evt.deviceId !== config.deviceId) return
  // 更新本地影子
  const idx = liveSelectedProps.value.findIndex(p => p.identifier === evt.identifier)
  if (idx >= 0) {
    liveSelectedProps.value[idx] = {
      ...liveSelectedProps.value[idx],
      value: evt.value == null ? null : String(evt.value),
      updatedAt: evt.updatedAt
    }
  } else {
    // 新出现的属性(物模型里有但之前没上报过)
    liveSelectedProps.value.push({
      identifier: evt.identifier,
      value: evt.value == null ? null : String(evt.value),
      updatedAt: evt.updatedAt
    } as any)
  }
  // 触发响应(属性闪一下)
  flashProp(evt.identifier)
  // 重新拉一次时序(便宜,200ms 后)
  if (evt.identifier === 'temperature') {
    setTimeout(() => refreshHistory(), 200)
  }
  // 记日志
  pushLog('recv', `[WS] shadow.update ${evt.deviceKey}.${evt.identifier} = ${evt.value}`)
}

const flashingProps = ref<Set<string>>(new Set())
function flashProp(id: string) {
  flashingProps.value.add(id)
  setTimeout(() => flashingProps.value.delete(id), 600)
}

function setupWS() {
  if (ws) ws.close()
  ws = new WSClient('/ws/shadow')
  ws.on('connected', () => { wsStatus.value = 'connected'; pushLog('info', '[WS] 已连接') })
  ws.on('disconnected', () => { wsStatus.value = 'disconnected' })
  ws.on('shadow.update', handleShadowUpdate)
  ws.connect()
}

onMounted(async () => {
  await loadOptions()
  if (config.protocol === 'MQTT') connectMqtt()
  // 首次进入拉一次快照
  manualRefresh()
  // 启动 WebSocket(替代轮询)
  setupWS()
})
onBeforeUnmount(() => {
  stopLoop()
  disconnectMqtt()
  if (ws) { ws.close(); ws = null }
  chart?.dispose?.()
})
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">设备调试模拟器</h2>

    <!-- 配置区 -->
    <div class="page-card config-bar">
      <el-form :inline="true" label-width="80px" size="default" @submit.prevent>
        <el-form-item label="产品">
          <el-select v-model="config.productId" placeholder="选产品" style="width: 200px" filterable>
            <el-option v-for="p in products" :key="p.id" :label="`${p.productKey} - ${p.productName}`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="设备">
          <el-select v-model="config.deviceId" placeholder="选设备" style="width: 200px" filterable>
            <el-option v-for="d in filteredDevices" :key="d.id" :label="`${d.deviceKey} (${d.deviceName})`" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="协议">
          <el-radio-group v-model="config.protocol">
            <el-radio value="MQTT">MQTT (WS)</el-radio>
            <el-radio value="TCP">TCP</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="config.protocol === 'MQTT'" label="Broker">
          <el-input v-model="config.brokerUrl" style="width: 240px" />
        </el-form-item>
        <el-form-item v-if="config.protocol === 'MQTT'" label="MQTT 状态">
          <el-tag :type="mqttStatus === 'connected' ? 'success' : mqttStatus === 'connecting' ? 'warning' : 'info'" size="small">
            <el-icon v-if="mqttStatus === 'connected'"><Connection /></el-icon>
            {{ mqttStatus }}
          </el-tag>
        </el-form-item>
        <el-form-item v-if="config.deviceId" label="密钥">
          <el-input v-model="config.secret" style="width: 280px" placeholder="自动从 /full 端点获取" />
        </el-form-item>
        <el-form-item v-if="config.deviceId" label="Topic">
          <el-input :model-value="renderedTopic" readonly style="width: 360px; font-family: monospace; font-size: 12px" />
        </el-form-item>
      </el-form>
    </div>

    <el-row :gutter="16">
      <!-- 左侧:数据生成 + 发送 -->
      <el-col :xs="24" :md="14">
        <div class="page-card">
          <h3 class="card-title">数据生成器 ({{ generators.length }} 个属性)</h3>
          <div class="gen-toolbar">
            <el-button :icon="Position" @click="addGenerator">添加属性</el-button>
            <el-button :icon="EditPen" :disabled="!thingModelProps.length" @click="importFromModel">
              从物模型导入
            </el-button>
            <span class="hint" v-if="thingModelProps.length">
              物模型 {{ thingModelProps.length }} 个属性可导入
            </span>
          </div>

          <el-table :data="generators" border size="small" empty-text="尚未添加属性">
            <el-table-column label="标识符" width="160">
              <template #default="{ row }">
                <el-input v-model="row.identifier" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="类型" width="110">
              <template #default="{ row }">
                <el-select v-model="row.type" size="small">
                  <el-option label="int" value="int" />
                  <el-option label="float" value="float" />
                  <el-option label="bool" value="bool" />
                  <el-option label="string" value="string" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="模式" width="110">
              <template #default="{ row }">
                <el-select v-model="row.mode" size="small">
                  <el-option label="随机" value="random" />
                  <el-option label="递增" value="inc" />
                  <el-option label="常量" value="const" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column v-if="generators.some(g => g.mode !== 'const' && g.type !== 'bool' && g.type !== 'string')" label="范围" width="180">
              <template #default="{ row }">
                <el-input-number v-if="row.mode === 'random' && (row.type === 'int' || row.type === 'float')" v-model="row.min" :min="-99999" :max="99999" size="small" controls-position="right" style="width: 80px" />
                <span v-if="row.mode === 'random' && (row.type === 'int' || row.type === 'float')" style="margin: 0 4px">~</span>
                <el-input-number v-if="row.mode === 'random' && (row.type === 'int' || row.type === 'float')" v-model="row.max" :min="-99999" :max="99999" size="small" controls-position="right" style="width: 80px" />
                <el-input-number v-if="row.mode === 'inc' && (row.type === 'int' || row.type === 'float')" v-model="row.value" :step="row.type === 'int' ? 1 : 0.1" size="small" controls-position="right" style="width: 110px" />
                <el-switch v-if="row.type === 'bool'" v-model="row.value" />
                <el-input v-if="row.type === 'string'" v-model="row.value" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="单位" width="80">
              <template #default="{ row }">
                <el-input v-model="row.unit" size="small" placeholder="℃" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="60">
              <template #default="{ $index }">
                <el-button link type="danger" :icon="Delete" @click="removeGenerator($index)" />
              </template>
            </el-table-column>
          </el-table>

          <div class="action-bar">
            <el-input-number v-model="loopInterval" :min="100" :max="60000" :step="100" size="default" style="width: 160px" />
            <span class="hint">ms 间隔</span>
            <el-button v-if="!looping" type="primary" :icon="VideoPlay" :disabled="!config.deviceId" @click="startLoop">开始连续</el-button>
            <el-button v-else type="danger" :icon="VideoPause" @click="stopLoop">停止</el-button>
            <el-button type="success" :icon="Position" :disabled="!config.deviceId || looping" :loading="sending" @click="sendOne">单次发送</el-button>
            <el-tag>seq = {{ seq }}</el-tag>
          </div>
        </div>

        <!-- 时序图 -->
        <div class="page-card">
          <h3 class="card-title">温度时序 (最近 5 分钟)</h3>
          <div ref="chartEl" class="chart"></div>
        </div>
      </el-col>

      <!-- 右侧:实时影子 + 日志 -->
      <el-col :xs="24" :md="10">
        <div class="page-card">
          <div class="card-title-row">
            <h3 class="card-title">当前设备影子 <el-tag size="small" :type="wsStatus === 'connected' ? 'success' : 'info'" style="margin-left: 8px">WS: {{ wsStatus }}</el-tag></h3>
          </div>
          <div v-if="!config.deviceId" class="empty-mini">先选设备</div>
          <div v-else>
            <div v-for="p in liveSelectedProps" :key="p.identifier" class="prop-row" :class="{ flash: flashingProps.has(p.identifier) }">
              <div class="prop-id">
                <span class="name">{{ p.name || p.identifier }}</span>
                <code>{{ p.identifier }}</code>
              </div>
              <div class="prop-val">
                <span v-if="p.value != null" :style="{ color: '#67c23a' }">{{ p.value }}</span>
                <span v-else class="muted">— 未上报</span>
                <span v-if="p.unit" class="unit">{{ p.unit }}</span>
              </div>
            </div>
          </div>
          <div style="display: flex; align-items: center; gap: 8px; margin-top: 8px">
            <el-button :icon="Refresh" size="small" @click="manualRefresh">手动刷新</el-button>
            <span class="text-muted" v-if="lastRefresh">上次刷新: {{ new Date(lastRefresh).toLocaleTimeString('zh-CN') }}</span>
          </div>
        </div>

        <div class="page-card">
          <div class="card-title-row">
            <h3 class="card-title">实时日志 ({{ logs.length }})</h3>
            <el-button link :icon="Delete" size="small" @click="clearLogs">清空</el-button>
          </div>
          <div class="log-panel">
            <div v-for="(l, i) in logs" :key="i" class="log-line" :class="l.level">
              <span class="log-time">{{ new Date(l.ts).toLocaleTimeString('zh-CN') }}</span>
              <span class="log-text"><pre>{{ l.text }}</pre></span>
            </div>
            <div v-if="!logs.length" class="empty-mini">暂无日志</div>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped lang="scss">
.config-bar { margin-bottom: 12px; padding: 16px; :deep(.el-form-item) { margin-bottom: 0; } }
.card-title { font-size: 15px; margin: 0 0 12px; color: #303133; display: flex; align-items: center; gap: 6px; }
.card-title-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; .card-title { margin: 0; } }
.hint { color: #909399; font-size: 12px; margin: 0 4px; }
.mb-16 { margin-bottom: 16px; }
.gen-toolbar { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
.action-bar { display: flex; align-items: center; gap: 8px; margin-top: 12px; padding-top: 12px; border-top: 1px solid #ebeef5; }
.chart { width: 100%; height: 220px; }

.prop-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 8px;
  margin: 0 -8px;
  border-bottom: 1px dashed #ebeef5;
  border-radius: 4px;
  transition: background 0.6s;
  &:last-child { border-bottom: none; }
  &.flash { background: #ecf5ff; }
  .prop-id { display: flex; align-items: center; gap: 8px; .name { font-weight: 500; } code { color: #909399; font-size: 11px; } }
  .prop-val { font-family: 'Menlo', monospace; font-weight: 600; .unit { color: #909399; font-weight: 400; margin-left: 4px; font-size: 12px; } }
  .muted { color: #c0c4cc; font-weight: 400; }
}

.log-panel {
  max-height: 400px;
  overflow-y: auto;
  background: #1e1e1e;
  border-radius: 4px;
  padding: 8px;
  font-family: 'Menlo', 'Consolas', monospace;
  font-size: 12px;
}
.log-line {
  display: flex;
  gap: 8px;
  padding: 2px 0;
  line-height: 1.5;
  &.send .log-text pre { color: #67c23a; }
  &.recv .log-text pre { color: #409eff; }
  &.ok .log-text pre { color: #67c23a; font-weight: 500; }
  &.err .log-text pre { color: #f56c6c; }
  &.info .log-text pre { color: #909399; }
  .log-time { color: #6088a0; flex-shrink: 0; }
  .log-text pre { margin: 0; white-space: pre-wrap; word-break: break-all; }
}
.empty-mini { text-align: center; color: #c0c4cc; padding: 24px 0; font-size: 13px; }
.text-muted { color: #909399; font-size: 12px; }
</style>