<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { Connection, Monitor, Box, Warning, Odometer, TrendCharts } from '@element-plus/icons-vue'

const stats = ref({
  deviceTotal: 0,
  deviceOnline: 0,
  productTotal: 0,
  alertPending: 0,
  todayMessages: 0,
  todayAlerts: 0
})

const recentAlerts = ref<any[]>([])
const recentDevices = ref<any[]>([])

let timer: any

async function loadData() {
  // TODO 调接口
  stats.value = {
    deviceTotal: 128,
    deviceOnline: 96,
    productTotal: 12,
    alertPending: 3,
    todayMessages: 24560,
    todayAlerts: 5
  }
  recentAlerts.value = [
    { id: 1, device: '温度传感器-001', level: 'WARN', content: '温度超过 35℃', time: '5 分钟前' },
    { id: 2, device: '电表-002', level: 'ERROR', content: '功率异常', time: '12 分钟前' }
  ]
}

onMounted(() => {
  loadData()
  timer = setInterval(loadData, 30000)
})
onBeforeUnmount(() => clearInterval(timer))
</script>

<template>
  <div class="page-container dashboard">
    <h2 class="page-title">工作台</h2>

    <el-row :gutter="16" class="mb-16">
      <el-col :xs="12" :sm="8" :md="4">
        <div class="stat-card stat-blue">
          <el-icon :size="32"><Monitor /></el-icon>
          <div class="stat-num">{{ stats.deviceOnline }} / {{ stats.deviceTotal }}</div>
          <div class="stat-label">在线设备</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <div class="stat-card stat-green">
          <el-icon :size="32"><Box /></el-icon>
          <div class="stat-num">{{ stats.productTotal }}</div>
          <div class="stat-label">产品数量</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <div class="stat-card stat-orange">
          <el-icon :size="32"><Warning /></el-icon>
          <div class="stat-num">{{ stats.alertPending }}</div>
          <div class="stat-label">待处理告警</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <div class="stat-card stat-purple">
          <el-icon :size="32"><Odometer /></el-icon>
          <div class="stat-num">{{ stats.todayMessages }}</div>
          <div class="stat-label">今日消息数</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <div class="stat-card stat-red">
          <el-icon :size="32"><TrendCharts /></el-icon>
          <div class="stat-num">{{ stats.todayAlerts }}</div>
          <div class="stat-label">今日告警</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <div class="stat-card stat-cyan">
          <el-icon :size="32"><Connection /></el-icon>
          <div class="stat-num">MQTT/TCP</div>
          <div class="stat-label">协议在线</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :xs="24" :md="14">
        <div class="page-card">
          <h3 class="card-title">最近告警</h3>
          <el-table :data="recentAlerts" stripe>
            <el-table-column prop="device" label="设备" />
            <el-table-column prop="level" label="级别" width="100">
              <template #default="{ row }">
                <el-tag :type="row.level === 'ERROR' ? 'danger' : 'warning'" size="small">
                  {{ row.level }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="content" label="内容" />
            <el-table-column prop="time" label="时间" width="120" />
          </el-table>
        </div>
      </el-col>
      <el-col :xs="24" :md="10">
        <div class="page-card">
          <h3 class="card-title">系统说明</h3>
          <ul class="info-list">
            <li>• 支持 MQTT / TCP 设备接入,通过协议适配层抽象</li>
            <li>• 物模型:产品 → 属性 / 事件 / 服务</li>
            <li>• 实时数据:WebSocket 推送 + TDengine 持久化</li>
            <li>• 规则引擎:SpEL 条件 + 多种动作(告警 / 通知 / 设备指令)</li>
            <li>• 多租户隔离 + RBAC 权限</li>
            <li>• 响应式 Web,支持手机端操作</li>
          </ul>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped lang="scss">
.dashboard { background: #f5f7fa; }

.stat-card {
  background: #fff;
  border-radius: 8px;
  padding: 20px 16px;
  text-align: center;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  margin-bottom: 12px;
}

.stat-num { font-size: 22px; font-weight: 600; color: #303133; }
.stat-label { font-size: 12px; color: #909399; }

.stat-blue   { color: #409eff; background: linear-gradient(135deg, #ecf5ff 0%, #fff 100%); }
.stat-green  { color: #67c23a; background: linear-gradient(135deg, #f0f9eb 0%, #fff 100%); }
.stat-orange { color: #e6a23c; background: linear-gradient(135deg, #fdf6ec 0%, #fff 100%); }
.stat-purple { color: #909399; background: linear-gradient(135deg, #f4f4f5 0%, #fff 100%); }
.stat-red    { color: #f56c6c; background: linear-gradient(135deg, #fef0f0 0%, #fff 100%); }
.stat-cyan   { color: #06b6d4; background: linear-gradient(135deg, #ecfeff 0%, #fff 100%); }

.card-title {
  font-size: 16px;
  margin: 0 0 16px;
  color: #303133;
}

.info-list {
  font-size: 14px;
  line-height: 2;
  color: #606266;
  li { padding: 2px 0; }
}
</style>
