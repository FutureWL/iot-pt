<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, computed } from 'vue'
import { Refresh, Connection, Position } from '@element-plus/icons-vue'
import { getRealtime, getRealtimeTimestamps, type RealtimeDevice } from '@/api/data/realtime'
import { WSClient } from '@/utils/ws'

const loading = ref(false)
const data = ref<RealtimeDevice[]>([])
const expanded = ref<Record<number, boolean>>({})

/** WS 状态: 'connecting' | 'connected' | 'disconnected' */
const wsStatus = ref<'connecting' | 'connected' | 'disconnected'>('connecting')
/** WS 最后一次失败的原因(断开/握手错误时填,排查用) */
const wsLastError = ref<string>('')

// ============ HTTP 轮询 ============
// 旧逻辑:WS 健康 30s / WS 断开 5s,会产生 5s 一刷全量的视觉干扰。
// 新逻辑:WS 健康时**不轮询**(WS 实时推,不需要再拉);
//        WS 断开时**不静默轮询**,直接告诉用户"通道断了,请手动刷新"。
//        这样出问题能马上被看到,不会偷偷用 stale 数据刷新页面。
let pollTimer: any

const POLL_INTERVAL_WS_OK = 30_000   // 保留,但默认不启用(见 restartPolling)

async function load() {
  loading.value = true
  try {
    const res: any = await getRealtime()
    data.value = res.data ?? []
  } finally {
    loading.value = false
  }
}

/** 增量更新:WebSocket 推过来的属性值直接 patch 到本地 */
function patchProperty(deviceId: number, identifier: string, value: any, updatedAt?: string) {
  const dev = data.value.find(d => d.deviceId === deviceId)
  if (!dev) return
  const prop = dev.properties.find(p => p.identifier === identifier)
  if (prop) {
    prop.value = value == null ? null : String(value)
    if (updatedAt) prop.updatedAt = updatedAt
    prop.recentTs = Date.now()   // WS 推送的就是最新的,标为 now
  } else {
    // 新出现的属性,动态加入
    dev.properties.push({
      identifier, value: value == null ? null : String(value),
      updatedAt, recentTs: Date.now()
    } as any)
  }
}

/** 增量更新:WebSocket 推过来的设备状态变化 */
function patchDeviceStatus(deviceId: number, status: number) {
  const dev = data.value.find(d => d.deviceId === deviceId)
  if (dev) {
    dev.status = status
    if (status === 0) dev.lastOnlineTime = undefined as any
  }
}

async function refreshTimestamps() {
  try {
    const res: any = await getRealtimeTimestamps()
    const map = res.data || {}
    let updated = 0
    for (const dev of data.value) {
      const idMap = map[String(dev.deviceId)] || map[dev.deviceId]
      if (!idMap) continue
      for (const p of dev.properties) {
        const ts = idMap[p.identifier]
        if (ts) { p.recentTs = ts; updated++ }
      }
    }
    return updated
  } catch { return 0 }
}

const totalCount = computed(() => data.value.length)
const onlineCount = computed(() => data.value.filter(d => d.status === 1).length)
const propCount = computed(() => data.value.reduce((s, d) => s + d.properties.length, 0))
const reportedCount = computed(() => data.value.reduce(
  (s, d) => s + d.properties.filter(p => p.value != null).length, 0))

function toggleDevice(d: RealtimeDevice) {
  expanded.value[d.deviceId] = !expanded.value[d.deviceId]
}

function formatTime(t?: string) {
  if (!t) return '—'
  return t
}

/** 把毫秒时间戳格式化为"X 秒前 / X 分钟前 / X 小时前" */
function timeAgo(ts?: number): string {
  if (!ts) return '—'
  const diff = Math.max(0, Date.now() - ts)
  if (diff < 5_000) return '刚刚'
  if (diff < 60_000) return `${Math.floor(diff / 1000)} 秒前`
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} 分钟前`
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} 小时前`
  return `${Math.floor(diff / 86_400_000)} 天前`
}

function valColor(v: any) {
  if (v == null) return '#c0c4cc'
  return '#67c23a'
}

// ============ WebSocket 客户端 ============
let ws: WSClient | null = null

function setupWS() {
  if (ws) ws.close()
  // 路径走 WSClient 默认值(读 VITE_WS_BASE_URL,默认 /api/ws),
  // 与后端 server.servlet.context-path=/api 对齐。
  ws = new WSClient()
  ws.on('connected', () => {
    wsStatus.value = 'connected'
    wsLastError.value = ''
    restartPolling(true)
  })
  ws.on('disconnected', (evt: any) => {
    wsStatus.value = 'disconnected'
    // 记录 onclose 的 code/reason,排查用
    wsLastError.value = (ws as any)?.lastError?.value
      || `code=${evt?.code ?? '?'}${evt?.reason ? ' ' + evt.reason : ''}`
    // WS 断开:**不**静默 5s 一刷全量。
    // 之前是 poll 全量,用户感受是"页面每隔几秒闪一下"。
    // 现在改成不轮询,只把状态暴露给用户,让用户决定是否手动刷新。
    restartPolling(false)
  })
  ws.on('shadow.update', (evt: any) => {
    patchProperty(evt.deviceId, evt.identifier, evt.value, evt.updatedAt)
  })
  ws.on('device.status', (evt: any) => {
    patchDeviceStatus(evt.deviceId, evt.status)
  })
  ws.connect()
}

/**
 * 根据 WS 状态调整轮询频率。
 * 注意:WS 健康时**不实际跑**轮询,只保留定时器句柄以便 cleanup。
 *       WS 断开时不静默轮询,只把状态呈现给 UI。
 */
function restartPolling(wsOk: boolean) {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
  if (!wsOk) {
    // 不开定时器。WS 断开 = 数据可能过期,让用户看到 alert 并自己点刷新。
    return
  }
  // WS 健康时也先不轮询:WS 推过来的属性已经包含 updatedAt,
  // "X 分钟前" 那个时间戳我们用 updatedAt 来计算就够了,
  // 不需要再发请求到后端查 TDengine。
  // 如果将来确实需要,可以恢复 setInterval(refreshTimestamps, POLL_INTERVAL_WS_OK)。
}

onMounted(async () => {
  await load()
  setupWS()
})
onBeforeUnmount(() => {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
  if (ws) { ws.close(); ws = null }
})
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="page-header">
      <h2 class="page-title">实时数据</h2>
      <div class="actions">
        <el-tag>设备 {{ totalCount }} 台</el-tag>
        <el-tag type="success">在线 {{ onlineCount }} 台</el-tag>
        <el-tag type="info">属性 {{ reportedCount }} / {{ propCount }} 已上报</el-tag>
        <el-tag :type="wsStatus === 'connected' ? 'success' : wsStatus === 'connecting' ? 'warning' : 'danger'" size="default">
          WS: {{ wsStatus }}
        </el-tag>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert
      :type="wsStatus === 'connected' ? 'success' : wsStatus === 'connecting' ? 'warning' : 'danger'"
      :closable="false"
      show-icon
      style="margin-bottom: 12px">
      <template v-if="wsStatus === 'connected'">
        实时推送已连接:属性变化通过 WebSocket 增量更新。
      </template>
      <template v-else-if="wsStatus === 'connecting'">
        正在连接实时通道……
      </template>
      <template v-else>
        <strong>实时通道断开,数据不会自动刷新。</strong>
        当前显示的可能是过期数据,请点击右上角「刷新」按钮手动拉取。
        <div v-if="wsLastError" style="margin-top: 4px; font-size: 12px; opacity: 0.85">
          原因: {{ wsLastError }}
        </div>
      </template>
    </el-alert>

    <div v-for="d in data" :key="d.deviceId" class="device-card page-card">
      <div class="device-row" @click="toggleDevice(d)">
        <el-icon class="caret"><Position v-if="expanded[d.deviceId]" /><Position v-else /></el-icon>
        <span class="device-key">
          <el-icon><Connection /></el-icon>
          {{ d.deviceName }}
          <el-tag size="small" type="info" style="margin-left: 6px">{{ d.deviceKey }}</el-tag>
        </span>
        <el-tag size="small">{{ d.productName }}</el-tag>
        <el-tag :type="d.status === 1 ? 'success' : 'info'" size="small">
          {{ d.status === 1 ? '在线' : '离线' }}
        </el-tag>
        <span class="last-online">最近上报: {{ formatTime(d.lastOnlineTime) }}</span>
        <el-tag v-if="d.location" size="small" type="info">{{ d.location }}</el-tag>
        <span class="property-summary">
          {{ d.properties.filter(p => p.value != null).length }} / {{ d.properties.length }} 个属性已上报
        </span>
      </div>

      <div v-show="expanded[d.deviceId]" class="property-grid">
        <div v-for="p in d.properties" :key="p.identifier" class="property-cell">
          <div class="prop-name">
            {{ p.name || p.identifier }}
            <span class="prop-identifier">{{ p.identifier }}</span>
          </div>
          <div class="prop-value" :style="{ color: valColor(p.value) }">
            <span v-if="p.value != null">{{ p.value }}</span>
            <span v-else class="not-reported">— 未上报</span>
            <span v-if="p.unit" class="prop-unit">{{ p.unit }}</span>
          </div>
          <div class="prop-meta">
            <el-tag size="small" :type="p.accessMode === 'ro' ? 'info' : 'success'">
              {{ p.accessMode }}
            </el-tag>
            <span class="prop-type">{{ p.type }}</span>
            <span v-if="p.recentTs" class="prop-time" :title="new Date(p.recentTs).toLocaleString('zh-CN')">
              {{ timeAgo(p.recentTs) }}
            </span>
            <span v-else-if="p.updatedAt" class="prop-time">{{ p.updatedAt }}</span>
          </div>
        </div>
      </div>
    </div>

    <el-empty v-if="!loading && data.length === 0" description="暂无设备" />
  </div>
</template>

<style scoped lang="scss">
.page-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  .page-title { margin: 0; flex: 1; }
  .actions { display: flex; align-items: center; gap: 8px; }
}
.device-card { margin-bottom: 12px; }
.device-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 4px;
  cursor: pointer;
  user-select: none;
  &:hover { background: #f5f7fa; }
  .caret { color: #909399; transition: transform 0.2s; }
  .device-key { font-weight: 500; display: flex; align-items: center; gap: 4px; }
  .last-online { color: #909399; font-size: 12px; }
  .property-summary { color: #409eff; font-size: 12px; margin-left: auto; }
}
.property-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
  padding: 12px 0 0;
  border-top: 1px solid #ebeef5;
  margin-top: 8px;
}
.property-cell {
  background: #fafafa;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 10px 12px;
  .prop-name { font-size: 12px; color: #606266; display: flex; align-items: center; gap: 6px; }
  .prop-identifier { color: #909399; font-size: 11px; font-family: monospace; }
  .prop-value { font-size: 18px; font-weight: 600; margin: 4px 0; font-family: 'Menlo', monospace;
    .prop-unit { font-size: 12px; color: #909399; margin-left: 4px; font-weight: 400; }
    .not-reported { color: #c0c4cc; font-size: 14px; font-weight: 400; }
  }
  .prop-meta { display: flex; align-items: center; gap: 6px; font-size: 11px; color: #909399; .prop-time { margin-left: auto; } }
}
</style>