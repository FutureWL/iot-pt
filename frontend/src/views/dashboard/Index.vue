<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  Monitor, Box, Warning, Odometer, Connection, TrendCharts, Refresh
} from '@element-plus/icons-vue'
import { getDashboardSummary, type DashboardSummary } from '@/api/dashboard'

const router = useRouter()
const loading = ref(false)
const data = ref<DashboardSummary | null>(null)
let timer: any

async function load() {
  loading.value = true
  try {
    const res: any = await getDashboardSummary()
    data.value = res.data
  } finally {
    loading.value = false
  }
}
function go(path: string) { router.push(path) }

const deviceOnlineRate = computed(() => {
  if (!data.value || data.value.deviceTotal === 0) return '0%'
  return Math.round(data.value.deviceByStatus.online / data.value.deviceTotal * 100) + '%'
})
const deviceOnlineRateNum = computed(() => {
  if (!data.value || data.value.deviceTotal === 0) return 0
  return Math.round(data.value.deviceByStatus.online / data.value.deviceTotal * 100)
})

const levelMap: Record<string, { label: string; color: string; tag: string }> = {
  INFO: { label: '信息', color: '#909399', tag: 'info' },
  WARN: { label: '警告', color: '#E6A23C', tag: 'warning' },
  ERROR: { label: '故障', color: '#F56C6C', tag: 'danger' },
  CRITICAL: { label: '紧急', color: '#F56C6C', tag: 'danger' }
}

function fmt(t?: string) {
  if (!t) return '—'
  return t
}

onMounted(() => {
  load()
  timer = setInterval(load, 30000)  // 30s 自动刷新
})
onBeforeUnmount(() => clearInterval(timer))
</script>

<template>
  <div class="page-container dashboard" v-loading="loading">
    <div class="page-header">
      <h2 class="page-title">工作台</h2>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <!-- 6 个核心统计卡 -->
    <el-row :gutter="16" class="mb-16">
      <el-col :xs="12" :sm="8" :md="4">
        <div class="stat-card stat-blue clickable" @click="go('/device/list')">
          <el-icon :size="32"><Monitor /></el-icon>
          <div class="stat-num">{{ data?.deviceTotal ?? 0 }}</div>
          <div class="stat-label">设备总数</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <div class="stat-card stat-green clickable" @click="go('/device/list')">
          <el-icon :size="32"><Connection /></el-icon>
          <div class="stat-num">{{ data?.deviceByStatus.online ?? 0 }}</div>
          <div class="stat-label">在线设备</div>
          <div class="stat-rate">在线率 {{ deviceOnlineRate }}</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <div class="stat-card stat-orange clickable" @click="go('/product')">
          <el-icon :size="32"><Box /></el-icon>
          <div class="stat-num">{{ data?.productTotal ?? 0 }}</div>
          <div class="stat-label">产品数量</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <div class="stat-card stat-red clickable" @click="go('/rule/alert')">
          <el-icon :size="32"><Warning /></el-icon>
          <div class="stat-num">{{ data?.pendingAlerts ?? 0 }}</div>
          <div class="stat-label">待处理告警</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <div class="stat-card stat-purple clickable" @click="go('/rule/alert')">
          <el-icon :size="32"><TrendCharts /></el-icon>
          <div class="stat-num">{{ data?.todayAlerts ?? 0 }}</div>
          <div class="stat-label">今日告警</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <div class="stat-card stat-cyan clickable" @click="go('/data/realtime')">
          <el-icon :size="32"><Odometer /></el-icon>
          <div class="stat-num">{{ data?.shadowTotal ?? 0 }}</div>
          <div class="stat-label">影子属性点</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <!-- 左:最近告警 -->
      <el-col :xs="24" :md="14">
        <div class="page-card">
          <h3 class="card-title">最近告警</h3>
          <el-table :data="data?.recentAlerts ?? []" stripe empty-text="暂无告警" size="default">
            <el-table-column label="级别" width="100">
              <template #default="{ row }">
                <el-tag :type="levelMap[row.level]?.tag as any" size="small">
                  {{ levelMap[row.level]?.label || row.level }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="title" label="内容" min-width="240" show-overflow-tooltip />
            <el-table-column label="设备" width="140">
              <template #default="{ row }">
                <el-tag size="small" type="info">{{ row.deviceKey }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="触发时间" width="170" />
            <template #empty>
              <div class="empty-tip">
                <el-icon :size="40" color="#67c23a"><Connection /></el-icon>
                <p>一切正常,暂无告警</p>
              </div>
            </template>
          </el-table>
        </div>
      </el-col>

      <!-- 右:最近上线设备 + 设备状态分布 -->
      <el-col :xs="24" :md="10">
        <div class="page-card">
          <h3 class="card-title">设备状态分布</h3>
          <div class="status-row" v-if="data">
            <div class="status-cell online">
              <div class="bar" :style="{ width: (data.deviceTotal ? data.deviceByStatus.online / data.deviceTotal * 100 : 0) + '%' }"></div>
              <span class="lbl">在线</span>
              <span class="num">{{ data.deviceByStatus.online }}</span>
            </div>
            <div class="status-cell offline">
              <div class="bar" :style="{ width: (data.deviceTotal ? data.deviceByStatus.offline / data.deviceTotal * 100 : 0) + '%' }"></div>
              <span class="lbl">离线</span>
              <span class="num">{{ data.deviceByStatus.offline }}</span>
            </div>
            <div class="status-cell disabled">
              <div class="bar" :style="{ width: (data.deviceTotal ? data.deviceByStatus.disabled / data.deviceTotal * 100 : 0) + '%' }"></div>
              <span class="lbl">禁用</span>
              <span class="num">{{ data.deviceByStatus.disabled }}</span>
            </div>
          </div>
        </div>

        <div class="page-card">
          <h3 class="card-title">最近在线设备</h3>
          <div class="online-list">
            <div v-for="d in data?.recentOnlineDevices ?? []" :key="d.id" class="online-item">
              <el-icon class="dot" color="#67c23a"><Connection /></el-icon>
              <div class="info">
                <div class="name">{{ d.deviceName }}
                  <el-tag size="small" type="info" style="margin-left:4px">{{ d.deviceKey }}</el-tag>
                </div>
                <div class="meta">{{ d.productName }} · {{ fmt(d.lastOnlineTime) }}</div>
              </div>
            </div>
            <el-empty v-if="(data?.recentOnlineDevices ?? []).length === 0" description="暂无在线设备" :image-size="60" />
          </div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <!-- 产品分布 -->
      <el-col :xs="24" :md="14">
        <div class="page-card">
          <h3 class="card-title">产品 / 设备分布</h3>
          <el-table :data="data?.productDistribution ?? []" stripe size="default">
            <el-table-column prop="productKey" label="产品 Key" width="180">
              <template #default="{ row }">
                <el-tag size="small" type="info">{{ row.productKey }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="productName" label="产品名" min-width="180" />
            <el-table-column label="设备数" width="200">
              <template #default="{ row }">
                <div class="count-bar">
                  <div class="bar-fill" :style="{ width: countBarWidth(row.count) }"></div>
                  <span class="count-num">{{ row.count }} 台</span>
                </div>
              </template>
            </el-table-column>
            <template #empty><el-empty description="暂无产品" /></template>
          </el-table>
        </div>
      </el-col>
      <!-- 平台能力 -->
      <el-col :xs="24" :md="10">
        <div class="page-card capability">
          <h3 class="card-title">平台能力</h3>
          <ul class="info-list">
            <li>✓ 支持 MQTT / TCP 设备接入,自动注册到 EMQX</li>
            <li>✓ 物模型:产品 → 属性 / 事件 / 服务,可视化编辑</li>
            <li>✓ 设备影子:实时记录每个属性最新值,支持模拟上报</li>
            <li>✓ TDengine 时序存储:支持 max/min/avg 实时聚合</li>
            <li>✓ 规则引擎:SpEL 条件 + 模板变量 + 告警动作</li>
            <li>✓ 多租户隔离:tenant_id 贯穿全链路</li>
            <li>✓ RBAC 权限:用户 → 角色 → 菜单,数据按 tenant 隔离</li>
          </ul>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped lang="scss">
.dashboard { background: #f5f7fa; }
.page-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  .page-title { margin: 0; flex: 1; }
}
.mb-16 { margin-bottom: 16px; }

.stat-card {
  background: #fff;
  border-radius: 8px;
  padding: 20px 16px;
  text-align: center;
  box-shadow: 0 1px 4px rgba(0,0,0,0.04);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  margin-bottom: 12px;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
  &.clickable:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0,0,0,0.08); }
}
.stat-num { font-size: 24px; font-weight: 600; color: #303133; font-family: 'Menlo', monospace; }
.stat-label { font-size: 12px; color: #909399; }
.stat-rate { font-size: 11px; color: #67c23a; margin-top: 2px; }
.stat-blue   { color: #409eff; background: linear-gradient(135deg, #ecf5ff 0%, #fff 100%); }
.stat-green  { color: #67c23a; background: linear-gradient(135deg, #f0f9eb 0%, #fff 100%); }
.stat-orange { color: #e6a23c; background: linear-gradient(135deg, #fdf6ec 0%, #fff 100%); }
.stat-red    { color: #f56c6c; background: linear-gradient(135deg, #fef0f0 0%, #fff 100%); }
.stat-purple { color: #909399; background: linear-gradient(135deg, #f4f4f5 0%, #fff 100%); }
.stat-cyan   { color: #06b6d4; background: linear-gradient(135deg, #ecfeff 0%, #fff 100%); }

.card-title {
  font-size: 16px;
  margin: 0 0 16px;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 8px;
  &::before {
    content: '';
    display: block;
    width: 3px;
    height: 14px;
    background: #409eff;
  }
}

.status-row { display: flex; flex-direction: column; gap: 12px; }
.status-cell {
  position: relative;
  height: 36px;
  background: #f5f7fa;
  border-radius: 4px;
  overflow: hidden;
  display: flex;
  align-items: center;
  padding: 0 12px;
  .bar { position: absolute; left: 0; top: 0; bottom: 0; opacity: 0.2; transition: width 0.6s; }
  .lbl { font-size: 13px; font-weight: 500; z-index: 1; }
  .num { margin-left: auto; font-family: 'Menlo', monospace; font-weight: 600; z-index: 1; }
  &.online { .bar { background: #67c23a; } .lbl, .num { color: #67c23a; } }
  &.offline { .bar { background: #909399; } .lbl, .num { color: #606266; } }
  &.disabled { .bar { background: #f56c6c; } .lbl, .num { color: #f56c6c; } }
}

.online-list {
  max-height: 280px;
  overflow-y: auto;
  .online-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 0;
    border-bottom: 1px dashed #ebeef5;
    &:last-child { border-bottom: none; }
    .dot { flex-shrink: 0; }
    .info { flex: 1; min-width: 0; }
    .name { font-size: 13px; font-weight: 500; }
    .meta { font-size: 11px; color: #909399; }
  }
}

.count-bar {
  position: relative;
  height: 22px;
  background: #f5f7fa;
  border-radius: 4px;
  .bar-fill {
    position: absolute; left: 0; top: 0; bottom: 0;
    background: linear-gradient(90deg, #409eff, #67c23a);
    border-radius: 4px;
    transition: width 0.6s;
  }
  .count-num {
    position: relative;
    z-index: 1;
    padding: 0 10px;
    line-height: 22px;
    font-size: 12px;
    font-weight: 500;
  }
}

.empty-tip {
  text-align: center;
  padding: 24px 0;
  color: #67c23a;
  p { margin-top: 8px; }
}

.capability .info-list {
  font-size: 13px;
  line-height: 2.2;
  color: #606266;
  margin: 0;
  padding-left: 4px;
  li { padding: 2px 0; }
}
</style>