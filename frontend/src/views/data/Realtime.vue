<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, computed } from 'vue'
import { Refresh, Connection, Position } from '@element-plus/icons-vue'
import { getRealtime, type RealtimeDevice } from '@/api/data/realtime'

const loading = ref(false)
const data = ref<RealtimeDevice[]>([])
const expanded = ref<Record<number, boolean>>({})
let timer: any

async function load() {
  loading.value = true
  try {
    const res: any = await getRealtime()
    data.value = res.data ?? []
  } finally {
    loading.value = false
  }
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

function valColor(v: any) {
  if (v == null) return '#c0c4cc'
  return '#67c23a'
}

onMounted(() => {
  load()
  timer = setInterval(load, 5000)  // 每 5 秒自动刷新
})
onBeforeUnmount(() => clearInterval(timer))
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="page-header">
      <h2 class="page-title">实时数据</h2>
      <div class="actions">
        <el-tag>设备 {{ totalCount }} 台</el-tag>
        <el-tag type="success">在线 {{ onlineCount }} 台</el-tag>
        <el-tag type="info">属性 {{ reportedCount }} / {{ propCount }} 已上报</el-tag>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert type="info" :closable="false" style="margin-bottom: 12px">
      自动每 5 秒刷新一次。点击设备行展开查看各属性当前值。值实时来自设备影子(MySQL)。
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
            <span v-if="p.updatedAt" class="prop-time">{{ p.updatedAt }}</span>
          </div>
        </div>
      </div>
    </div>

    <el-empty v-if="!loading && data.length === 0" description="暂无在线设备" />
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