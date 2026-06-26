<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Edit, Delete, Refresh, Connection } from '@element-plus/icons-vue'
import {
  getDeviceShadows,
  upsertDeviceShadow,
  deleteDeviceShadow,
  type IotDeviceShadowVO,
  type IotDeviceShadowDTO
} from '@/api/iot/deviceShadow'
import { getDevice, type IotDeviceVO } from '@/api/iot/device'

const route = useRoute()
const deviceId = Number(route.query.deviceId)

const device = ref<IotDeviceVO | null>(null)
const shadows = ref<IotDeviceShadowVO[]>([])
const loading = ref(false)

// ========== 数据类型选项(从物模型 type 字段推断) ==========
const editingValue = ref<string>('')
const editDialog = ref(false)
const editForm = reactive<{
  productId: number
  identifier: string
  name: string
  type: string
  unit: string
  value: string  // 文本框值
}>({ productId: 0, identifier: '', name: '', type: '', unit: '', value: '' })

async function load() {
  if (!deviceId) { ElMessage.error('缺少 deviceId'); return }
  loading.value = true
  try {
    const [dRes, sRes]: any[] = await Promise.all([
      getDevice(deviceId),
      getDeviceShadows(deviceId)
    ])
    device.value = dRes.data
    shadows.value = sRes.data ?? []
  } finally {
    loading.value = false
  }
}

const statusMap: Record<number, { label: string; type: string }> = {
  0: { label: '离线', type: 'info' },
  1: { label: '在线', type: 'success' },
  2: { label: '禁用', type: 'danger' }
}

const valueColor = computed(() => (v?: string) => v && v !== 'null' ? '#67c23a' : '#c0c4cc')

function formatValue(v?: string): string {
  if (v === undefined || v === null || v === 'null') return '—'
  return v
}

// 把字符串 value 转回原生类型(用于显示和编辑)
function parseValue(v: string | undefined, type: string): any {
  if (v === undefined || v === 'null') return null
  try {
    if (type === 'int') return parseInt(JSON.parse(v))
    if (type === 'float') return parseFloat(JSON.parse(v))
    if (type === 'bool') return JSON.parse(v) === true
    return JSON.parse(v)
  } catch {
    return v
  }
}

function openEditValue(row: IotDeviceShadowVO) {
  Object.assign(editForm, {
    productId: device.value!.productId,
    identifier: row.identifier,
    name: row.name || row.identifier,
    type: row.type || 'string',
    unit: row.unit || '',
    value: row.valueJson && row.valueJson !== 'null' ? row.valueJson : ''
  })
  editingValue.value = editForm.value
  editDialog.value = true
}

async function onSaveValue() {
  if (!device.value) return
  let v: any = editForm.value
  try {
    if (editForm.type === 'int') v = parseInt(editForm.value) || 0
    else if (editForm.type === 'float') v = parseFloat(editForm.value) || 0
    else if (editForm.type === 'bool') v = editForm.value === 'true'
    else v = editForm.value
  } catch (e) {
    ElMessage.error('值格式错误')
    return
  }
  try {
    const dto: IotDeviceShadowDTO = {
      productId: device.value.productId,
      identifier: editForm.identifier,
      value: v
    }
    await upsertDeviceShadow(device.value.id, dto)
    ElMessage.success('影子已更新')
    editDialog.value = false
    load()
  } catch (e) {}
}

async function onClear(row: IotDeviceShadowVO) {
  await ElMessageBox.confirm(`清除属性「${row.identifier}」的当前值?`, '清除确认', { type: 'warning' })
  try {
    await deleteDeviceShadow(deviceId, row.identifier)
    ElMessage.success('已清除')
    load()
  } catch {}
}

async function onSimulate() {
  // 模拟设备上报:批量给所有 rw 属性赋个随机值
  if (!device.value) return
  const rwProps = shadows.value.filter(s => s.accessMode !== 'ro' || !s.valueJson || s.valueJson === 'null')
  if (rwProps.length === 0) {
    ElMessage.warning('所有属性都已有值,无需模拟')
    return
  }
  let count = 0
  for (const p of rwProps) {
    let v: any
    if (p.type === 'int') v = Math.floor(Math.random() * 100)
    else if (p.type === 'float') v = parseFloat((Math.random() * 100).toFixed(2))
    else if (p.type === 'bool') v = Math.random() > 0.5
    else v = `value-${Math.floor(Math.random() * 1000)}`
    try {
      await upsertDeviceShadow(deviceId, {
        productId: device.value.productId, identifier: p.identifier, value: v
      })
      count++
    } catch {}
  }
  ElMessage.success(`已模拟上报 ${count} 个属性`)
  load()
}

onMounted(load)
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div v-if="device" class="device-header">
      <div class="info">
        <h2 class="page-title">
          <el-icon><Connection /></el-icon>
          设备影子 — {{ device.deviceName }}
          <el-tag size="small" style="margin-left: 8px">{{ device.deviceKey }}</el-tag>
        </h2>
        <div class="meta">
          <el-tag :type="statusMap[device.status]?.type as any" size="small">
            {{ statusMap[device.status]?.label }}
          </el-tag>
          <span class="text-muted">产品: {{ device.productName }} ({{ device.productKey }})</span>
          <span class="text-muted">协议: {{ device.protocol }}</span>
          <span v-if="device.location" class="text-muted">位置: {{ device.location }}</span>
        </div>
      </div>
      <div class="actions">
        <el-button :icon="Refresh" @click="load">刷新</el-button>
        <el-button type="primary" @click="onSimulate">模拟设备上报</el-button>
      </div>
    </div>

    <div class="page-card">
      <el-alert type="info" :closable="false" style="margin-bottom: 12px">
        <b>设备影子</b>展示设备最新一次上报的属性值。协议层接通前,可用"模拟上报"按钮或单个"编辑"按钮来手动设置/清空。
      </el-alert>

      <el-table :data="shadows" border size="default" empty-text="该产品暂无物模型属性">
        <el-table-column prop="identifier" label="标识符" min-width="140">
          <template #default="{ row }">
            <code class="identifier">{{ row.identifier }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" min-width="120" />
        <el-table-column label="类型 / 单位" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ row.type }}</el-tag>
            <span v-if="row.unit" class="text-muted"> {{ row.unit }}</span>
          </template>
        </el-table-column>
        <el-table-column label="权限" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.accessMode === 'ro' ? 'info' : 'success'">{{ row.accessMode }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="当前值" min-width="200">
          <template #default="{ row }">
            <span :style="{ color: valueColor(row.valueJson), fontWeight: 500, fontFamily: 'monospace' }">
              {{ formatValue(row.valueJson) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="最近更新" width="170" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="openEditValue(row)">编辑</el-button>
            <el-button link type="danger" :icon="Delete" :disabled="!row.valueJson || row.valueJson === 'null'"
              @click="onClear(row)">清除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 编辑属性值 -->
    <el-dialog v-model="editDialog" title="编辑属性值" width="420px" destroy-on-close>
      <el-form label-width="80px" @submit.prevent>
        <el-form-item label="属性">
          <el-input :model-value="`${editForm.name} (${editForm.identifier})`" disabled />
        </el-form-item>
        <el-form-item label="类型">
          <el-input :model-value="`${editForm.type}${editForm.unit ? ' / ' + editForm.unit : ''}`" disabled />
        </el-form-item>
        <el-form-item label="值">
          <el-input v-if="editForm.type === 'bool'" v-model="editForm.value" placeholder="true / false">
            <template #append>
              <el-select v-model="editForm.value" style="width: 100px">
                <el-option label="true" value="true" />
                <el-option label="false" value="false" />
              </el-select>
            </template>
          </el-input>
          <el-input v-else v-model="editForm.value"
            :type="editForm.type === 'string' ? 'textarea' : 'text'"
            :placeholder="editForm.type === 'int' ? '整数' : editForm.type === 'float' ? '小数' : '字符串'" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialog = false">取消</el-button>
        <el-button type="primary" @click="onSaveValue">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.device-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
  .info { flex: 1; }
  .page-title { display: flex; align-items: center; gap: 8px; margin: 0 0 8px; }
  .meta { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
  .actions { display: flex; gap: 8px; }
}
.text-muted { color: #909399; font-size: 13px; }
.identifier {
  background: #f0f9eb;
  color: #67c23a;
  padding: 2px 6px;
  border-radius: 3px;
  font-family: 'Menlo', 'Consolas', monospace;
  font-size: 12px;
}
</style>