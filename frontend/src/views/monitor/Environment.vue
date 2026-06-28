<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Refresh, Cloudy, Warning } from '@element-plus/icons-vue'
import { listEnvironmentRealtime, type EnvironmentRealtimeVO } from '@/api/monitor/environment'

const loading = ref(false)
const list = ref<EnvironmentRealtimeVO[]>([])

async function load() {
  loading.value = true
  try {
    const res: any = await listEnvironmentRealtime()
    list.value = res.data ?? []
  } finally {
    loading.value = false
  }
}

const stats = ref({
  deviceCount: 0,
  avgTemp: 0,
  avgHumidity: 0,
  waterCount: 0,
  condensationCount: 0
})

// 计算统计
function calcStats() {
  if (list.value.length === 0) return
  stats.value = {
    deviceCount: list.value.length,
    avgTemp: list.value.reduce((s, x) => s + x.temperature, 0) / list.value.length,
    avgHumidity: list.value.reduce((s, x) => s + x.humidity, 0) / list.value.length,
    waterCount: list.value.filter(x => x.waterStatus === 1).length,
    condensationCount: list.value.filter(x => x.condensationRisk).length
  }
}

onMounted(async () => { await load(); calcStats() })
</script>

<template>
  <div
    v-loading="loading"
    class="page-container env-page"
  >
    <div class="page-header">
      <h2 class="page-title">
        环境监测
      </h2>
      <el-button
        :icon="Refresh"
        @click="load"
      >
        刷新
      </el-button>
    </div>

    <el-row
      :gutter="16"
      class="mb-16"
    >
      <el-col
        :xs="12"
        :sm="6"
      >
        <div class="env-card">
          <el-icon
            :size="22"
            color="#409eff"
          >
            <Cloudy />
          </el-icon>
          <div class="env-label">
            监测设备数
          </div>
          <div class="env-num">
            {{ stats.deviceCount }}
          </div>
        </div>
      </el-col>
      <el-col
        :xs="12"
        :sm="6"
      >
        <div class="env-card">
          <div class="env-label">
            平均温度
          </div>
          <div class="env-num">
            {{ stats.avgTemp.toFixed(1) }}<span class="env-unit"> ℃</span>
          </div>
        </div>
      </el-col>
      <el-col
        :xs="12"
        :sm="6"
      >
        <div class="env-card">
          <div class="env-label">
            平均湿度
          </div>
          <div class="env-num">
            {{ stats.avgHumidity.toFixed(1) }}<span class="env-unit"> %</span>
          </div>
        </div>
      </el-col>
      <el-col
        :xs="12"
        :sm="6"
      >
        <div class="env-card alert-card">
          <el-icon
            :size="22"
            color="#f56c6c"
          >
            <Warning />
          </el-icon>
          <div class="env-label">
            水浸 / 凝露预警
          </div>
          <div class="env-num">
            {{ stats.waterCount + stats.condensationCount }}
          </div>
        </div>
      </el-col>
    </el-row>

    <div class="page-card">
      <h3 class="card-title">
        柜内微环境实时数据
      </h3>
      <el-table
        :data="list"
        stripe
        empty-text="暂无环境数据"
        border
      >
        <el-table-column
          prop="deviceKey"
          label="设备"
          width="140"
        >
          <template #default="{ row }">
            <el-tag
              size="small"
              type="info"
            >
              {{ row.deviceKey }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="deviceName"
          label="名称"
          min-width="160"
          show-overflow-tooltip
        />
        <el-table-column
          label="温度(℃)"
          width="100"
        >
          <template #default="{ row }">
            <span :class="{ 'text-danger': row.temperature > 60 }">{{ row.temperature.toFixed(1) }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="湿度(%)"
          width="100"
        >
          <template #default="{ row }">
            <span :class="{ 'text-warning': row.humidity > 70 }">{{ row.humidity.toFixed(1) }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="水浸状态"
          width="100"
        >
          <template #default="{ row }">
            <el-tag
              :type="row.waterStatus === 1 ? 'danger' : 'success'"
              size="small"
            >
              {{ row.waterStatus === 1 ? '水浸' : '正常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="倾角(°)"
          width="100"
        >
          <template #default="{ row }">
            <span :class="{ 'text-danger': row.tiltAngle > 5 }">{{ row.tiltAngle.toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="振动(g RMS)"
          width="120"
        >
          <template #default="{ row }">
            <span :class="{ 'text-warning': row.vibrationRMS > 2 }">{{ row.vibrationRMS.toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="凝露预警"
          width="100"
        >
          <template #default="{ row }">
            <el-tag
              v-if="row.condensationRisk"
              type="warning"
              size="small"
            >
              预警
            </el-tag>
            <span
              v-else
              class="text-success"
            >正常</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="ts"
          label="采集时间"
          min-width="170"
        />
      </el-table>
    </div>

    <div class="page-card mt-16">
      <h3 class="card-title">
        监测说明
      </h3>
      <ul class="info-list">
        <li>温度阈值: &gt; 60 ℃ 触发告警</li>
        <li>湿度阈值: &gt; 70 % 触发凝露预警(与温度联合判断)</li>
        <li>倾角阈值: &gt; 5° 表示柜体倾斜,需现场查看</li>
        <li>振动阈值: &gt; 2g RMS 提示设备异常震动</li>
      </ul>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.env-page { background: var(--iot-bg-page); }
.page-header { display: flex; align-items: center; gap: $spacing-12; margin-bottom: $spacing-16; .page-title { margin: 0; flex: 1; } }
.mb-16 { margin-bottom: $spacing-16; }
.mt-16 { margin-top: $spacing-16; }

.env-card {
  background: var(--iot-bg-card); border-radius: $radius-large; padding: $spacing-20 $spacing-16;
  text-align: center; box-shadow: var(--iot-shadow-light); margin-bottom: $spacing-12;
  display: flex; flex-direction: column; align-items: center; gap: $spacing-4;
}
.env-card.alert-card { background: linear-gradient(135deg, var(--iot-color-danger-light) 0%, #fff 100%); }
.env-label { font-size: $font-size-extra-small; color: var(--iot-text-secondary); }
.env-num { font-size: 32px; font-weight: $font-weight-bold; font-family: var(--iot-font-family-code); color: var(--iot-text-primary); }
.env-unit { font-size: $font-size-small; color: var(--iot-text-secondary); font-weight: $font-weight-normal; }

.card-title {
  font-size: $font-size-medium; margin: 0 0 $spacing-16;
  color: var(--iot-text-primary); display: flex; align-items: center; gap: $spacing-8;
  &::before { content: ''; display: block; width: 3px; height: 14px; background: var(--iot-color-primary); }
}

.info-list { font-size: $font-size-small; color: var(--iot-text-regular); line-height: 2; padding-left: $spacing-16; list-style: disc; }
</style>