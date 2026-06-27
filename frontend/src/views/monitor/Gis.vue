<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { Location, Connection, Warning, OfficeBuilding, Aim, Refresh, FullScreen } from '@element-plus/icons-vue'
import { listGisDevices, type GisDeviceVO } from '@/api/monitor/gis'

const router = useRouter()
const loading = ref(false)
const mapLoading = ref(true)
const mapError = ref<string | null>(null)
const devices = ref<GisDeviceVO[]>([])
const keyword = ref('')
const selected = ref<GisDeviceVO | null>(null)
const drawerVisible = ref(false)

// 地图实例(AMap 加载完成后赋值)
let AMap: any = null
let map: any = null
let cluster: any = null
const mapEl = ref<HTMLDivElement>()

// 状态配色:与设计 token 一致
const statusMap: Record<number, { label: string; color: string; type: string }> = {
  0: { label: '离线', color: '#909399', type: 'info' },
  1: { label: '在线', color: '#67c23a', type: 'success' },
  2: { label: '禁用', color: '#f56c6c', type: 'danger' }
}

// WGS-84 → GCJ-02 火星坐标转换(国网 GPS 设备默认 WGS-84)
function wgs84ToGcj02(lng: number, lat: number): [number, number] {
  const PI = 3.14159265358979324
  const a = 6378240.0
  const ee = 0.00669342162296594323
  function outOfChina(l: number, w: number) { return w < 72.004 || w > 137.8347 || l < 0.8293 || l > 55.8271 }
  function transformLat(x: number, y: number) {
    let ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x))
    ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0
    ret += (20.0 * Math.sin(y * PI) + 40.0 * Math.sin(y / 3.0 * PI)) * 2.0 / 3.0
    ret += (160.0 * Math.sin(y / 12.0 * PI) + 320 * Math.sin(y * PI / 30.0)) * 2.0 / 3.0
    return ret
  }
  function transformLng(x: number, y: number) {
    let ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x))
    ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0
    ret += (20.0 * Math.sin(x * PI) + 40.0 * Math.sin(x / 3.0 * PI)) * 2.0 / 3.0
    ret += (150.0 * Math.sin(x / 12.0 * PI) + 300.0 * Math.sin(x / 30.0 * PI)) * 2.0 / 3.0
    return ret
  }
  if (outOfChina(lng, lat)) return [lng, lat]
  let dLat = transformLat(lng - 105.0, lat - 35.0)
  let dLng = transformLng(lng - 105.0, lat - 35.0)
  const radLat = lat / 180.0 * PI
  let magic = Math.sin(radLat)
  magic = 1 - ee * magic * magic
  const sqrtMagic = Math.sqrt(magic)
  dLat = (dLat * 180.0) / ((a * (1 - ee)) / (magic * sqrtMagic) * PI)
  dLng = (dLng * 180.0) / (a / sqrtMagic * Math.cos(radLat) * PI)
  return [lng + dLng, lat + dLat]
}

async function initMap() {
  if (!mapEl.value) return
  const key = import.meta.env.VITE_AMAP_KEY
  if (!key) {
    mapError.value = '未配置 VITE_AMAP_KEY,请在 .env.local 设置'
    mapLoading.value = false
    return
  }
  try {
    // 动态导入避免 SSR 报错
    const AMapLoader = (await import('@amap/amap-jsapi-loader')).default
    AMap = await AMapLoader.load({
      key,
      version: '2.0',
      // securityJsCode 类型定义里没有,但运行时支持(TS 类型不全)
      ...(import.meta.env.VITE_AMAP_SECURITY_CODE
        ? { securityJsCode: import.meta.env.VITE_AMAP_SECURITY_CODE as any }
        : {}),
      plugins: ['AMap.Marker', 'AMap.MarkerCluster', 'AMap.InfoWindow', 'AMap.Scale', 'AMap.ToolBar']
    } as any)

    map = new AMap.Map(mapEl.value, {
      zoom: 11,
      center: [116.397428, 39.90923],  // 默认北京天安门
      viewMode: '2D',
      mapStyle: 'amap://styles/normal'
    })

    // 控件
    map.addControl(new AMap.Scale())
    map.addControl(new AMap.ToolBar({ position: 'RB' }))

    mapLoading.value = false
  } catch (e: any) {
    mapError.value = `地图加载失败: ${e?.message ?? e}`
    mapLoading.value = false
  }
}

async function renderMarkers() {
  if (!map || !AMap || devices.value.length === 0) return
  // 清旧
  if (cluster) { map.remove(cluster); cluster = null }

  const points: any[] = []
  for (const d of devices.value) {
    const [gcjLng, gcjLat] = wgs84ToGcj02(d.lng, d.lat)
    const marker = new AMap.Marker({
      position: [gcjLng, gcjLat],
      title: `${d.deviceName} (${d.deviceKey})`,
      content: `<div class="iot-marker iot-marker-${d.status === 1 ? 'online' : d.status === 2 ? 'disabled' : 'offline'}">
        <span class="dot"></span>
      </div>`,
      offset: new AMap.Pixel(-12, -12)
    })
    marker.on('click', () => onDeviceClick(d))
    points.push(marker)
  }

  // 大量点位用聚合(>20 自动启用)
  if (points.length > 20) {
    cluster = new AMap.MarkerCluster(map, points, {
      gridSize: 60,
      maxZoom: 16,
      renderClusterMarker: (context: any) => {
        const count = context.count
        const dangerCount = context.markers.filter((m: any) => m.getTitle().includes('🚨')).length
        const size = count < 10 ? 36 : count < 100 ? 44 : 52
        const color = dangerCount > 0 ? '#f56c6c' : '#409eff'
        return new AMap.Marker({
          position: context.clusterData?.lngLat ?? context.center,
          content: `<div class="iot-cluster" style="width:${size}px;height:${size}px;background:${color};">${count}</div>`,
          offset: new AMap.Pixel(-size / 2, -size / 2)
        })
      }
    })
  } else {
    map.add(points)
  }

  // 自适应缩放
  if (points.length > 0) {
    map.setFitView(points, false, [40, 40, 40, 40])
  }
}

async function load() {
  loading.value = true
  try {
    const res: any = await listGisDevices()
    devices.value = res.data ?? []
    await nextTick()
    await renderMarkers()
  } finally {
    loading.value = false
  }
}

function onDeviceClick(d: GisDeviceVO) {
  selected.value = d
  drawerVisible.value = true
  // 地图中心移过去
  if (map) {
    const [gcjLng, gcjLat] = wgs84ToGcj02(d.lng, d.lat)
    map.setCenter([gcjLng, gcjLat])
    map.setZoom(15)
  }
}

function closeDrawer() {
  drawerVisible.value = false
  setTimeout(() => { selected.value = null }, 300)
}

function goToDetail(d: GisDeviceVO) {
  router.push(`/device/list?deviceId=${d.deviceId}`)
}

function onFullScreen() {
  if (!mapEl.value) return
  if (document.fullscreenElement) document.exitFullscreen()
  else mapEl.value.requestFullscreen()
}

onMounted(async () => {
  await initMap()
  await load()
})
onBeforeUnmount(() => {
  cluster = null
  if (map) {
    map.destroy()
    map = null
  }
})
</script>

<template>
  <div class="page-container gis-page" v-loading="loading">
    <div class="page-header">
      <h2 class="page-title">GIS 地图 · 高德</h2>
      <div class="header-tools">
        <el-input
          v-model="keyword"
          placeholder="搜索设备名 / Key"
          clearable
          :prefix-icon="Aim"
          style="width: 240px"
        />
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="gis-layout">
      <!-- 左:设备列表 -->
      <div class="page-card device-panel">
        <div class="page-toolbar">
          <div class="device-summary">
            <div class="summary-item online">
              <span class="dot"></span>在线 {{ devices.filter(d => d.status === 1).length }}
            </div>
            <div class="summary-item offline">
              <span class="dot"></span>离线 {{ devices.filter(d => d.status === 0).length }}
            </div>
            <div class="summary-item danger">
              <span class="dot"></span>告警 {{ devices.filter(d => (d.alertCount ?? 0) > 0).length }}
            </div>
          </div>
        </div>
        <div class="device-list">
          <div v-for="d in devices.filter(d => !keyword || d.deviceName.includes(keyword) || d.deviceKey.includes(keyword))"
               :key="d.deviceId"
               class="device-item"
               :class="{ active: selected?.deviceId === d.deviceId }"
               @click="onDeviceClick(d)">
            <span class="status-dot" :style="{ background: statusMap[d.status]?.color }"></span>
            <div class="device-info">
              <div class="device-name">{{ d.deviceName }}</div>
              <div class="device-meta">
                <el-tag :type="statusMap[d.status]?.type as any" size="small">{{ statusMap[d.status]?.label }}</el-tag>
                <span class="device-key">{{ d.deviceKey }}</span>
              </div>
            </div>
            <el-badge v-if="d.alertCount" :value="d.alertCount" type="danger" />
          </div>
          <el-empty v-if="devices.length === 0" description="暂无设备" />
        </div>
      </div>

      <!-- 右:高德地图 -->
      <div class="page-card map-panel">
        <div ref="mapEl" class="map-container"></div>

        <!-- 加载中 -->
        <div v-if="mapLoading" class="map-overlay">
          <el-icon class="is-loading" :size="32"><Refresh /></el-icon>
          <p>地图加载中...</p>
        </div>

        <!-- 错误 -->
        <div v-else-if="mapError" class="map-overlay">
          <el-icon :size="48" color="#f56c6c"><Warning /></el-icon>
          <p>{{ mapError }}</p>
          <p class="text-secondary text-xs">请在 frontend/.env.local 设置 VITE_AMAP_KEY</p>
        </div>

        <!-- 全屏按钮 -->
        <el-button class="fullscreen-btn" :icon="FullScreen" circle @click="onFullScreen" />
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
            <el-descriptions-item label="经度(WGS-84)">{{ selected.lng.toFixed(6) }}</el-descriptions-item>
            <el-descriptions-item label="纬度(WGS-84)">{{ selected.lat.toFixed(6) }}</el-descriptions-item>
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

.gis-layout { flex: 1; display: grid; grid-template-columns: 320px 1fr; gap: $spacing-16; min-height: 0; }
@media (max-width: $breakpoint-md) { .gis-layout { grid-template-columns: 1fr; } }

.device-panel { display: flex; flex-direction: column; padding: $spacing-12; min-height: 0; }
.device-summary { display: flex; gap: $spacing-12; font-size: $font-size-extra-small; width: 100%; }
.summary-item { display: flex; align-items: center; gap: $spacing-4; padding: $spacing-4 $spacing-8; border-radius: $radius-small; background: var(--iot-bg-page); }
.summary-item .dot { width: 8px; height: 8px; border-radius: 50%; background: var(--iot-color-info); }
.summary-item.online .dot { background: var(--iot-color-success); }
.summary-item.offline .dot { background: var(--iot-text-disabled); }
.summary-item.danger .dot { background: var(--iot-color-danger); }

.device-list { flex: 1; overflow-y: auto; }
.device-item {
  display: flex; align-items: center; gap: $spacing-8;
  padding: $spacing-8; border-radius: $radius-base;
  cursor: pointer; transition: background $transition-fast;
  border: 1px solid transparent;
  &:hover { background: var(--iot-bg-hover); }
  &.active { background: var(--iot-color-primary-light-9); border-color: var(--iot-color-primary-light-5); }
}
.status-dot { width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0; box-shadow: 0 0 6px currentColor; }
.device-info { flex: 1; min-width: 0; }
.device-name { font-size: $font-size-small; font-weight: $font-weight-medium; }
.device-meta { display: flex; align-items: center; gap: $spacing-8; margin-top: 2px; }
.device-key { color: var(--iot-text-secondary); font-size: $font-size-extra-small; font-family: var(--iot-font-family-code); }

.map-panel { padding: 0; overflow: hidden; position: relative; }
.map-container { width: 100%; height: 100%; min-height: 480px; }
.map-overlay {
  position: absolute; inset: 0;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  background: rgba(255,255,255,0.95); gap: $spacing-12; color: var(--iot-text-secondary);
}
.fullscreen-btn {
  position: absolute; right: $spacing-16; top: $spacing-16; z-index: 10;
  background: var(--iot-bg-card); box-shadow: var(--iot-shadow-md);
}
.fullscreen-btn:hover { background: var(--iot-bg-card); }

.drawer-content { padding: $spacing-16; }
.drawer-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: $spacing-16; h3 { margin: 0; } }
.drawer-actions { margin-top: $spacing-16; }
</style>

<!-- 全局 marker 样式(必须 unscoped) -->
<style lang="scss">
.iot-marker {
  width: 24px; height: 24px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  background: #fff;
  box-shadow: 0 2px 6px rgba(0,0,0,0.3);
  border: 2px solid #fff;
  cursor: pointer;
  transition: transform 0.2s;
  &:hover { transform: scale(1.2); }
  .dot {
    width: 10px; height: 10px; border-radius: 50%;
    box-shadow: 0 0 6px currentColor;
  }
}
.iot-marker-online { color: #67c23a; .dot { background: #67c23a; } }
.iot-marker-offline { color: #909399; .dot { background: #909399; } }
.iot-marker-disabled { color: #f56c6c; .dot { background: #f56c6c; } }

.iot-cluster {
  border-radius: 50%; color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-weight: 600; font-size: 14px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.3);
  border: 2px solid rgba(255,255,255,0.9);
  cursor: pointer;
}
</style>