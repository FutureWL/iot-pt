<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { Location, Search, Connection, Warning, OfficeBuilding, Aim, Refresh } from '@element-plus/icons-vue'
import { listGisDevices, type GisDeviceVO } from '@/api/monitor/gis'

const router = useRouter()
const loading = ref(false)
const devices = ref<GisDeviceVO[]>([])
const keyword = ref('')
const selected = ref<GisDeviceVO | null>(null)
const drawerVisible = ref(false)
const mapType = ref<'amap' | 'baidu' | 'tian' | 'placeholder'>('placeholder')

const filteredDevices = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return devices.value
  return devices.value.filter(d =>
    d.deviceName.toLowerCase().includes(k) ||
    d.deviceKey.toLowerCase().includes(k) ||
    (d.address ?? '').toLowerCase().includes(k)
  )
})

const statusMap: Record<number, { label: string; color: string; type: string }> = {
  0: { label: '离线', color: '#909399', type: 'info' },
  1: { label: '在线', color: '#67c23a', type: 'success' },
  2: { label: '禁用', color: '#f56c6c', type: 'danger' }
}

async function load() {
  loading.value = true
  try {
    const res: any = await listGisDevices()
    devices.value = res.data ?? []
  } finally {
    loading.value = false
  }
}

function onDeviceClick(d: GisDeviceVO) {
  selected.value = d
  drawerVisible.value = true
}

function closeDrawer() {
  drawerVisible.value = false
  // 保留 selected 让动画过渡时仍有数据
  setTimeout(() => { selected.value = null }, 300)
}

function goToDetail(d: GisDeviceVO) {
  router.push(`/device/list?deviceId=${d.deviceId}`)
}

onMounted(load)
</script>

<template>
  <div class="page-container gis-page" v-loading="loading">
    <div class="page-header">
      <h2 class="page-title">GIS 地图</h2>
      <div class="header-tools">
        <el-radio-group v-model="mapType" size="small">
          <el-radio-button value="amap">高德</el-radio-button>
          <el-radio-button value="baidu">百度</el-radio-button>
          <el-radio-button value="tian">天地图</el-radio-button>
          <el-radio-button value="placeholder">占位</el-radio-button>
        </el-radio-group>
        <el-button :icon="Refresh" @click="load" />
      </div>
    </div>

    <div class="gis-layout">
      <!-- 左侧:设备列表 -->
      <div class="page-card device-panel">
        <div class="page-toolbar">
          <el-input v-model="keyword" placeholder="搜索设备名 / Key / 地址" clearable :prefix-icon="Search" />
        </div>
        <div class="device-list">
          <div v-for="d in filteredDevices" :key="d.deviceId"
            class="device-item" :class="{ active: selected?.deviceId === d.deviceId }"
            @click="onDeviceClick(d)">
            <el-icon class="device-icon" :color="statusMap[d.status]?.color">
              <Aim />
            </el-icon>
            <div class="device-info">
              <div class="device-name">{{ d.deviceName }}</div>
              <div class="device-meta">
                <el-tag :type="statusMap[d.status]?.type as any" size="small">{{ statusMap[d.status]?.label }}</el-tag>
                <span class="text-secondary text-xs ml-8">{{ d.deviceKey }}</span>
              </div>
              <div class="device-addr text-secondary text-xs" v-if="d.address">📍 {{ d.address }}</div>
            </div>
            <el-button v-if="d.alertCount" link type="danger" size="small">
              <el-icon><Warning /></el-icon> {{ d.alertCount }}
            </el-button>
          </div>
          <el-empty v-if="filteredDevices.length === 0" description="暂无设备" />
        </div>
      </div>

      <!-- 右侧:地图区域 -->
      <div class="page-card map-panel">
        <div v-if="mapType === 'placeholder'" class="map-placeholder">
          <el-icon :size="80" color="#c0c4cc"><Location /></el-icon>
          <h3 class="placeholder-title">地图组件待接入</h3>
          <p class="placeholder-text">
            地图底图选型待 <b>OQ-007</b> 客户确认(高德 / 百度 / 天地图 三选一)。<br />
            选型确认后此区域将渲染实际地图并支持点位聚合(cluster)。
          </p>
          <div class="placeholder-stats">
            <div class="stat-mini">
              <div class="stat-mini-num">{{ devices.length }}</div>
              <div class="stat-mini-label">设备总数</div>
            </div>
            <div class="stat-mini online">
              <div class="stat-mini-num">{{ devices.filter(d => d.status === 1).length }}</div>
              <div class="stat-mini-label">在线</div>
            </div>
            <div class="stat-mini alert">
              <div class="stat-mini-num">{{ devices.filter(d => (d.alertCount ?? 0) > 0).length }}</div>
              <div class="stat-mini-label">告警点位</div>
            </div>
          </div>
        </div>
        <div v-else class="map-other">
          <el-alert :title="`地图底图: ${mapType} (待接入)`" type="warning" :closable="false" show-icon>
            已选择地图类型,但 SDK 集成待 OQ-007 决策确认。当前页面展示占位。
          </el-alert>
          <div class="map-fake">
            <el-icon :size="120" color="#dcdfe6"><OfficeBuilding /></el-icon>
            <p>地图区域占位 {{ mapType }}</p>
          </div>
        </div>
      </div>
    </div>

    <!-- 详情抽屉 -->
    <el-drawer v-model="drawerVisible" :show-close="false" direction="rtl" size="420px" :with-header="false" @close="closeDrawer">
      <template v-if="selected">
        <div class="drawer-content">
          <div class="drawer-header">
            <h3>{{ selected.deviceName }}</h3>
            <el-button @click="closeDrawer">关闭</el-button>
          </div>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="设备 Key">{{ selected.deviceKey }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="statusMap[selected.status]?.type as any" size="small">
                {{ statusMap[selected.status]?.label }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="经度">{{ selected.lng }}</el-descriptions-item>
            <el-descriptions-item label="纬度">{{ selected.lat }}</el-descriptions-item>
            <el-descriptions-item label="地址" v-if="selected.address">{{ selected.address }}</el-descriptions-item>
            <el-descriptions-item label="告警数">{{ selected.alertCount ?? 0 }}</el-descriptions-item>
          </el-descriptions>
          <div class="drawer-actions">
            <el-button type="primary" @click="goToDetail(selected)">查看设备详情</el-button>
          </div>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.gis-page { background: var(--iot-bg-page); height: calc(100vh - 56px); display: flex; flex-direction: column; }
.page-header { display: flex; align-items: center; gap: $spacing-12; margin-bottom: $spacing-16; .page-title { margin: 0; flex: 1; } }
.header-tools { display: flex; align-items: center; gap: $spacing-12; }
.ml-8 { margin-left: $spacing-8; }

.gis-layout { flex: 1; display: grid; grid-template-columns: 320px 1fr; gap: $spacing-16; min-height: 0; }
@media (max-width: $breakpoint-md) { .gis-layout { grid-template-columns: 1fr; } }

.device-panel { display: flex; flex-direction: column; padding: $spacing-12; min-height: 0; }
.device-list { flex: 1; overflow-y: auto; }
.device-item {
  display: flex; align-items: center; gap: $spacing-8;
  padding: $spacing-8; border-radius: $radius-base;
  cursor: pointer; transition: background $transition-fast;
  border: 1px solid transparent;
  &:hover { background: var(--iot-bg-hover); }
  &.active { background: var(--iot-color-primary-light-9); border-color: var(--iot-color-primary-light-5); }
}
.device-icon { flex-shrink: 0; font-size: 18px; }
.device-info { flex: 1; min-width: 0; }
.device-name { font-size: $font-size-small; font-weight: $font-weight-medium; }
.device-meta { display: flex; align-items: center; margin-top: 2px; }
.device-addr { margin-top: 2px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.map-panel { padding: 0; overflow: hidden; }
.map-placeholder {
  height: 100%; min-height: 480px;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  background: linear-gradient(135deg, var(--iot-bg-page) 0%, var(--iot-bg-card) 100%);
  text-align: center; padding: $spacing-24;
}
.placeholder-title { font-size: $font-size-large; color: var(--iot-text-primary); margin: $spacing-16 0 $spacing-8; }
.placeholder-text { color: var(--iot-text-secondary); font-size: $font-size-small; line-height: 1.8; max-width: 480px; }
.placeholder-stats { display: flex; gap: $spacing-24; margin-top: $spacing-24; }
.stat-mini { text-align: center; padding: $spacing-12 $spacing-24; background: var(--iot-bg-card); border-radius: $radius-large; box-shadow: var(--iot-shadow-light); min-width: 96px; }
.stat-mini-num { font-size: $font-size-huge; font-weight: $font-weight-semibold; color: var(--iot-color-primary); font-family: var(--iot-font-family-code); }
.stat-mini.online .stat-mini-num { color: var(--iot-color-success); }
.stat-mini.alert .stat-mini-num { color: var(--iot-color-danger); }
.stat-mini-label { font-size: $font-size-extra-small; color: var(--iot-text-secondary); margin-top: $spacing-4; }

.map-other { padding: $spacing-16; height: 100%; display: flex; flex-direction: column; gap: $spacing-16; }
.map-fake { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; color: var(--iot-text-secondary); background: var(--iot-bg-page); border-radius: $radius-large; p { margin-top: $spacing-12; } }

.drawer-content { padding: $spacing-16; }
.drawer-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: $spacing-16; h3 { margin: 0; } }
.drawer-actions { margin-top: $spacing-16; }
</style>