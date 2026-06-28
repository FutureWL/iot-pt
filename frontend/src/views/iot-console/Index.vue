<script setup lang="ts">
/**
 * IoT 控制台 - 实时监控设备流量
 *
 * 4 tabs:
 *   1. 概览  - 在线数 / TX/RX / 错误数 / TPS
 *   2. 设备  - 在线设备列表 + 踢下线按钮
 *   3. 消息  - 实时 SSE 消息流(Wireshark 风格)
 *   4. 协议  - MQTT/TCP 状态 + 重启按钮
 */
import { ref, onMounted, onBeforeUnmount, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Cpu, Monitor, ChatLineRound, Connection,
  Refresh, VideoPlay, VideoPause, Delete, Lightning,
  DataLine, Warning, CircleClose
} from '@element-plus/icons-vue'
import {
  kickDevice, restartProtocol,
  subscribeStream,
  type ConsoleStatusVO, ConsoleDeviceVO, ConsoleEnvelopeVO
} from '@/api/iot/console'
// getStatus/getDevices 已移除 — 所有状态由 SSE 推送

// ============ 状态 ============
const activeTab = ref('overview')
const status = ref<ConsoleStatusVO>({})
const devices = ref<ConsoleDeviceVO[]>([])
const messages = ref<ConsoleEnvelopeVO[]>([])
const sseConnected = ref(false)
const ssePaused = ref(false)

// SSE 句柄
let stream: { close: () => void; source: EventSource } | null = null

// 协议列表(从 status 时顺便展示;后端不直接给协议状态,从 devices 推断)
const protocols = computed(() => {
  const set = new Set<string>()
  devices.value.forEach(d => set.add(d.protocol))
  return Array.from(set)
})

// ============ 定时刷新概览/设备 ============
// ============ SSE 流(所有状态都由后端推送) ============
function startStream() {
  if (stream) return
  stream = subscribeStream({
    onOpen: () => { sseConnected.value = true },
    onError: () => {
      // SSE 错误时(EventSource 会自动重连)仅更新状态,不弹 toast
      sseConnected.value = false
    },
    onMessage: (env) => {
      if (ssePaused.value) return
      messages.value.push(env)
      // 限制前端最大 500 条(防止内存爆)
      if (messages.value.length > 500) {
        messages.value.splice(0, messages.value.length - 500)
      }
    },
    // status: 后端每 5 秒推一次,直接覆盖本地状态
    onStatus: (s) => { status.value = s },
    // devices: 后端每 5 秒推一次,直接覆盖
    onDevices: (d) => { devices.value = d }
  })
}

function stopStream() {
  if (stream) {
    stream.close()
    stream = null
  }
  sseConnected.value = false
}

// ============ 控制动作 ============
async function onKick(row: ConsoleDeviceVO) {
  try {
    await ElMessageBox.confirm(
      `确认踢设备 [${row.deviceKey}] 下线?`,
      '踢设备',
      { type: 'warning' }
    )
    const res = await kickDevice(row.deviceKey)
    if (res.ok) {
      ElMessage.success(res.msg)
      // 设备列表会在 5 秒内由后端 SSE 推送更新
    } else {
      ElMessage.warning(res.msg)
    }
  } catch {
    // 用户取消
  }
}

async function onRestartProtocol(name: string) {
  try {
    await ElMessageBox.confirm(
      `确认重启协议适配器 [${name}]?\n(短暂断开所有 ${name} 设备)`,
      '重启协议',
      { type: 'warning' }
    )
    const res = await restartProtocol(name)
    if (res.ok) {
      ElMessage.success(res.msg)
    } else {
      ElMessage.warning(res.msg)
    }
  } catch {
    // 用户取消
  }
}

function onClearMessages() {
  messages.value = []
}

function onTogglePause() {
  ssePaused.value = !ssePaused.value
  ElMessage.info(ssePaused.value ? '已暂停(新消息暂存)' : '已恢复')
}

// ============ 工具 ============
function fmtTime(ts?: number) {
  if (!ts) return '-'
  const d = new Date(ts)
  return d.toLocaleTimeString('zh-CN', { hour12: false }) +
    '.' + String(d.getMilliseconds()).padStart(3, '0')
}

function durationText(connectTime?: number) {
  if (!connectTime) return '-'
  const ms = Date.now() - connectTime
  if (ms < 60_000) return `${Math.floor(ms / 1000)}s`
  if (ms < 3600_000) return `${Math.floor(ms / 60_000)}m ${Math.floor(ms % 60_000 / 1000)}s`
  return `${Math.floor(ms / 3600_000)}h ${Math.floor(ms % 3600_000 / 60_000)}m`
}

function typeColor(type: string) {
  switch (type) {
    case 'PROPERTY_REPORT': return '#67C23A'
    case 'EVENT_REPORT': return '#E6A23C'
    case 'ONLINE': return '#409EFF'
    case 'OFFLINE': return '#909399'
    default: return '#F56C6C'
  }
}

function typeTag(type: string): 'success' | 'warning' | 'info' | 'primary' | 'danger' {
  switch (type) {
    case 'PROPERTY_REPORT': return 'success'
    case 'EVENT_REPORT': return 'warning'
    case 'ONLINE': return 'primary'
    case 'OFFLINE': return 'info'
    default: return 'danger'
  }
}

function tryParsePayload(s?: string): any {
  if (!s) return null
  try { return JSON.parse(s) } catch { return s }
}

// ============ 生命周期 ============
onMounted(() => {
  // 只连 SSE。所有状态(status/devices/msg)由后端推送
  // SSE 连接时后端立即推一次 status + devices + 50 条历史 msg
  startStream()
})

onBeforeUnmount(() => {
  stopStream()
})
</script>

<template>
  <div class="iot-console">
    <!-- 顶部状态栏 -->
    <div class="topbar">
      <div class="topbar-left">
        <el-tag
          :type="sseConnected ? 'success' : 'danger'"
          size="small"
          effect="dark"
        >
          <el-icon><Connection /></el-icon>
          SSE {{ sseConnected ? '已连接' : '已断开' }}
        </el-tag>
        <el-tag
          v-if="ssePaused"
          type="warning"
          size="small"
        >
          已暂停
        </el-tag>
        <span class="topbar-text">缓冲: {{ messages.length }} 条</span>
      </div>
      <div class="topbar-right">
        <el-button
          :icon="ssePaused ? VideoPlay : VideoPause"
          size="small"
          @click="onTogglePause"
        >
          {{ ssePaused ? '恢复' : '暂停' }}
        </el-button>
        <el-button
          :icon="Delete"
          size="small"
          @click="onClearMessages"
        >
          清空
        </el-button>
      </div>
    </div>

    <!-- Tab 切换 -->
    <el-tabs
      v-model="activeTab"
      class="console-tabs"
    >
      <!-- ========== Tab 1: 概览 ========== -->
      <el-tab-pane
        label="概览"
        name="overview"
      >
        <template #label>
          <span><el-icon><DataLine /></el-icon> 概览</span>
        </template>
        <div class="overview-grid">
          <el-card
            class="metric-card"
            shadow="hover"
          >
            <div class="metric-label">
              <el-icon><Monitor /></el-icon> 在线设备
            </div>
            <div
              class="metric-value"
              :class="{ zero: !status.onlineDevices }"
            >
              {{ status.onlineDevices ?? 0 }}
            </div>
          </el-card>

          <el-card
            class="metric-card"
            shadow="hover"
          >
            <div class="metric-label">
              <el-icon><Cpu /></el-icon> RX 消息总数
            </div>
            <div class="metric-value primary">
              {{ status.rxTotal ?? 0 }}
            </div>
          </el-card>

          <el-card
            class="metric-card"
            shadow="hover"
          >
            <div class="metric-label">
              <el-icon><Cpu /></el-icon> TX 消息总数
            </div>
            <div class="metric-value success">
              {{ status.txTotal ?? 0 }}
            </div>
          </el-card>

          <el-card
            class="metric-card"
            shadow="hover"
          >
            <div class="metric-label">
              <el-icon><Warning /></el-icon> 错误总数
            </div>
            <div
              class="metric-value"
              :class="{ danger: (status.errTotal ?? 0) > 0 }"
            >
              {{ status.errTotal ?? 0 }}
            </div>
          </el-card>

          <el-card
            class="metric-card"
            shadow="hover"
          >
            <div class="metric-label">
              <el-icon><Lightning /></el-icon> RX TPS
            </div>
            <div class="metric-value primary">
              {{ status.rxTps ?? 0 }}
            </div>
          </el-card>

          <el-card
            class="metric-card"
            shadow="hover"
          >
            <div class="metric-label">
              <el-icon><Lightning /></el-icon> TX TPS
            </div>
            <div class="metric-value success">
              {{ status.txTps ?? 0 }}
            </div>
          </el-card>

          <el-card
            class="metric-card"
            shadow="hover"
          >
            <div class="metric-label">
              <el-icon><DataLine /></el-icon> 抓包缓冲
            </div>
            <div class="metric-value">
              {{ status.spyBufferSize ?? 0 }} / 1000
            </div>
          </el-card>

          <el-card
            class="metric-card"
            shadow="hover"
          >
            <div class="metric-label">
              <el-icon><Cpu /></el-icon> 运行时长
            </div>
            <div class="metric-value small">
              {{ durationText(status.ts ? status.ts - 1000 : undefined) }}
            </div>
          </el-card>
        </div>
      </el-tab-pane>

      <!-- ========== Tab 2: 设备 ========== -->
      <el-tab-pane name="devices">
        <template #label>
          <span><el-icon><Monitor /></el-icon> 设备 ({{ devices.length }})</span>
        </template>
        <el-table
          :data="devices"
          stripe
          empty-text="暂无在线设备"
        >
          <el-table-column
            prop="deviceKey"
            label="设备 Key"
            min-width="200"
          />
          <el-table-column
            prop="productKey"
            label="产品 Key"
            min-width="180"
          />
          <el-table-column
            prop="protocol"
            label="协议"
            width="100"
          >
            <template #default="{ row }">
              <el-tag
                size="small"
                :type="row.protocol === 'mqtt' ? 'success' : 'primary'"
              >
                {{ row.protocol }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            prop="remoteAddress"
            label="IP 地址"
            min-width="150"
          />
          <el-table-column
            label="连接时长"
            min-width="120"
          >
            <template #default="{ row }">
              {{ durationText(row.connectTime) }}
            </template>
          </el-table-column>
          <el-table-column
            label="最后活跃"
            min-width="120"
          >
            <template #default="{ row }">
              {{ fmtTime(row.lastActiveTime) }}
            </template>
          </el-table-column>
          <el-table-column
            label="操作"
            width="120"
            fixed="right"
          >
            <template #default="{ row }">
              <el-button
                type="danger"
                size="small"
                :icon="CircleClose"
                @click="onKick(row)"
              >
                踢下线
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ========== Tab 3: 实时消息 ========== -->
      <el-tab-pane name="messages">
        <template #label>
          <span><el-icon><ChatLineRound /></el-icon> 实时消息 ({{ messages.length }})</span>
        </template>
        <div class="message-list">
          <div
            v-for="m in messages.slice().reverse()"
            :key="m.id"
            class="message-row"
          >
            <span class="msg-time">{{ fmtTime(m.receivedAt) }}</span>
            <el-tag
              size="small"
              :type="typeTag(m.type)"
              effect="dark"
            >
              {{ m.type }}
            </el-tag>
            <el-tag
              size="small"
              :type="m.protocol === 'mqtt' ? 'success' : 'primary'"
            >
              {{ m.protocol }}
            </el-tag>
            <span class="msg-device">{{ m.deviceKey }}</span>
            <span class="msg-product">[{{ m.productKey }}]</span>
            <span
              v-if="m.remoteAddress"
              class="msg-ip"
            >{{ m.remoteAddress }}</span>
            <details
              v-if="m.payload"
              class="msg-payload"
            >
              <summary>payload</summary>
              <pre>{{ JSON.stringify(tryParsePayload(m.payload), null, 2) }}</pre>
            </details>
          </div>
          <el-empty
            v-if="messages.length === 0"
            description="暂无消息"
          />
        </div>
      </el-tab-pane>

      <!-- ========== Tab 4: 协议控制 ========== -->
      <el-tab-pane name="protocols">
        <template #label>
          <span><el-icon><Cpu /></el-icon> 协议控制</span>
        </template>
        <el-table
          :data="protocols"
          stripe
          empty-text="当前无在线设备,无协议活动"
        >
          <el-table-column
            prop="value"
            label="协议"
            min-width="120"
          >
            <template #default="{ row }">
              <el-tag
                size="small"
                :type="row === 'mqtt' ? 'success' : 'primary'"
              >
                {{ row }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态">
            <template #default="{ row }">
              <el-tag
                type="success"
                size="small"
              >
                运行中
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            label="操作"
            width="200"
          >
            <template #default="{ row }">
              <el-button
                type="warning"
                size="small"
                :icon="Refresh"
                @click="onRestartProtocol(row)"
              >
                重启适配器
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-alert
          class="protocol-tip"
          type="info"
          show-icon
          :closable="false"
          title="协议适配器说明"
          description="重启 MQTT 适配器会短暂断开所有 MQTT 设备(秒级),TCP 同理。请在维护窗口操作。"
        />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped lang="scss">
.iot-console {
  padding: 16px;
  background: #f5f7fa;
  min-height: calc(100vh - 60px);
}

.topbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding: 8px 16px;
  background: #fff;
  border-radius: 4px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);

  &-left {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  &-right {
    display: flex;
    gap: 8px;
  }

  &-text {
    font-size: 13px;
    color: #909399;
  }
}

.console-tabs {
  background: #fff;
  padding: 16px;
  border-radius: 4px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

// ===== 概览 =====
.overview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
  margin-top: 16px;
}

.metric-card {
  text-align: center;

  :deep(.el-card__body) {
    padding: 20px 16px;
  }
}

.metric-label {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
}

.metric-value {
  font-size: 36px;
  font-weight: 600;
  color: #303133;
  line-height: 1.2;

  &.zero {
    color: #c0c4cc;
  }

  &.primary { color: #409EFF; }
  &.success { color: #67C23A; }
  &.danger  { color: #F56C6C; }
  &.small   { font-size: 24px; }
}

// ===== 实时消息(Wireshark 风格) =====
.message-list {
  max-height: calc(100vh - 280px);
  overflow-y: auto;
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 8px;
  border-radius: 4px;
}

.message-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  border-bottom: 1px solid #2d2d2d;

  &:hover {
    background: #252526;
  }
}

.msg-time {
  color: #858585;
  font-size: 12px;
  flex-shrink: 0;
}

.msg-device {
  color: #4ec9b0;
  font-weight: 600;
}

.msg-product {
  color: #858585;
  font-size: 11px;
}

.msg-ip {
  color: #9cdcfe;
  font-size: 11px;
}

.msg-payload {
  width: 100%;
  margin-top: 4px;

  summary {
    color: #858585;
    cursor: pointer;
    font-size: 11px;
    user-select: none;

    &:hover { color: #d4d4d4; }
  }

  pre {
    margin: 4px 0 0 0;
    padding: 8px;
    background: #0e0e0e;
    color: #ce9178;
    border-radius: 2px;
    overflow-x: auto;
    font-size: 12px;
    line-height: 1.5;
  }
}

// ===== 协议 =====
.protocol-tip {
  margin-top: 16px;
}
</style>