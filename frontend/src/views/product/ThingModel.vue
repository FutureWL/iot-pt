<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, ArrowLeft, DocumentCopy, View } from '@element-plus/icons-vue'
import {
  getProduct,
  updateProduct,
  type IotProductVO
} from '@/api/iot/product'

// ========== 物模型类型定义 ==========
type DataType = 'int' | 'float' | 'bool' | 'string' | 'enum' | 'date'
type AccessMode = 'ro' | 'rw' | 'wo'
type EventType = 'info' | 'warn' | 'error'
type CallType = 'async' | 'sync'

interface Spec {
  min?: string
  max?: string
  step?: string
  options?: { value: string; label: string }[]
}

interface Property {
  identifier: string
  name: string
  type: DataType
  accessMode: AccessMode
  required: boolean
  unit?: string
  specs?: Spec
  description?: string
}

interface Param {
  identifier: string
  name: string
  type: DataType
  required: boolean
  specs?: Spec
}

interface IotEvent {
  identifier: string
  name: string
  type: EventType
  outputParams: Param[]
  description?: string
}

interface Service {
  identifier: string
  name: string
  callType: CallType
  inputParams: Param[]
  outputParams: Param[]
  description?: string
}

interface ThingModel {
  properties: Property[]
  events: IotEvent[]
  services: Service[]
}

// ========== 路由 / 加载 ==========
const route = useRoute()
const router = useRouter()
const productId = Number(route.params.id)

const product = ref<IotProductVO | null>(null)
const tsl = reactive<ThingModel>({ properties: [], events: [], services: [] })
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res: any = await getProduct(productId)
    product.value = res.data
    try {
      const parsed: ThingModel = JSON.parse(res.data.thingModel || '{}')
      tsl.properties = parsed.properties ?? []
      tsl.events = parsed.events ?? []
      tsl.services = parsed.services ?? []
    } catch {
      tsl.properties = []; tsl.events = []; tsl.services = []
    }
  } finally {
    loading.value = false
  }
}

// ========== Tabs ==========
const activeTab = ref<'properties' | 'events' | 'services'>('properties')

// ========== JSON 预览 ==========
const tslJson = computed(() => JSON.stringify(tsl, null, 2))

function copyJson() {
  navigator.clipboard.writeText(tslJson.value).then(
    () => ElMessage.success('TSL JSON 已复制到剪贴板'),
    () => ElMessage.error('复制失败,请手动选中复制')
  )
}

// ========== 属性编辑 ==========
const PROPERTY_TYPES: { value: DataType; label: string }[] = [
  { value: 'int', label: '整数 (int)' },
  { value: 'float', label: '小数 (float)' },
  { value: 'bool', label: '布尔 (bool)' },
  { value: 'string', label: '字符串 (string)' },
  { value: 'enum', label: '枚举 (enum)' },
  { value: 'date', label: '时间 (date)' }
]
const ACCESS_MODES: { value: AccessMode; label: string; desc: string }[] = [
  { value: 'ro', label: '只读', desc: '设备上报,平台只读' },
  { value: 'rw', label: '读写', desc: '平台可下发,设备可上报' },
  { value: 'wo', label: '只写', desc: '平台下发,设备不主动上报' }
]

const propDialog = ref(false)
const propMode = ref<'create' | 'edit'>('create')
const editingPropIndex = ref(-1)
const propForm = reactive<Property>({
  identifier: '', name: '', type: 'float', accessMode: 'ro', required: true,
  unit: '', specs: { min: '', max: '', step: '' }, description: ''
})
const propFormRef = ref()

const propRules = {
  identifier: [
    { required: true, message: '请输入标识符', trigger: 'blur' },
    { pattern: /^[a-z][a-zA-Z0-9_]{0,31}$/, message: '小写字母开头,只能含字母数字下划线', trigger: 'blur' }
  ],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }]
}

function openPropCreate() {
  propMode.value = 'create'
  Object.assign(propForm, {
    identifier: '', name: '', type: 'float', accessMode: 'ro', required: true,
    unit: '', specs: { min: '', max: '', step: '' }, description: ''
  })
  propDialog.value = true
}

function openPropEdit(idx: number) {
  propMode.value = 'edit'
  editingPropIndex.value = idx
  Object.assign(propForm, JSON.parse(JSON.stringify(tsl.properties[idx])))
  if (!propForm.specs) propForm.specs = { min: '', max: '', step: '' }
  propDialog.value = true
}

async function onPropSubmit() {
  if (!propFormRef.value) return
  let valid = false
  try { valid = await propFormRef.value.validate() } catch { valid = false }
  if (!valid) return

  // 唯一性
  const exists = tsl.properties.findIndex((p, i) =>
    p.identifier === propForm.identifier && i !== editingPropIndex.value)
  if (exists >= 0) {
    ElMessage.error(`标识符「${propForm.identifier}」已存在`)
    return
  }

  // 清理空 specs
  const cleaned: Property = { ...propForm }
  if (propForm.type === 'enum') {
    // enum 走 options,其他清空
    cleaned.specs = { options: propForm.specs?.options ?? [] }
  } else if (propForm.type === 'int' || propForm.type === 'float') {
    cleaned.specs = {
      min: propForm.specs?.min || undefined,
      max: propForm.specs?.max || undefined,
      step: propForm.specs?.step || undefined
    }
  } else {
    cleaned.specs = undefined
  }
  if (!cleaned.unit) cleaned.unit = undefined

  if (propMode.value === 'create') {
    tsl.properties.push(cleaned)
    ElMessage.success('属性已添加')
  } else {
    tsl.properties.splice(editingPropIndex.value, 1, cleaned)
    ElMessage.success('属性已更新')
  }
  propDialog.value = false
}

function onPropDelete(idx: number) {
  const p = tsl.properties[idx]
  ElMessageBox.confirm(`确认删除属性「${p.name}」?`, '删除确认', { type: 'warning' })
    .then(() => { tsl.properties.splice(idx, 1); ElMessage.success('已删除') })
    .catch(() => {})
}

// enum 选项编辑
const enumInput = ref('')
function addEnumOption() {
  const [v, l] = enumInput.value.split(':').map(s => s.trim())
  if (!v || !l) { ElMessage.warning('格式: 值:标签,例如 0:关闭'); return }
  if (!propForm.specs) propForm.specs = {}
  if (!propForm.specs.options) propForm.specs.options = []
  if (propForm.specs.options.find(o => o.value === v)) {
    ElMessage.warning('值已存在'); return
  }
  propForm.specs.options.push({ value: v, label: l })
  enumInput.value = ''
}
function removeEnumOption(idx: number) {
  propForm.specs?.options?.splice(idx, 1)
}

// ========== 事件编辑 ==========
const EVENT_TYPES: { value: EventType; label: string; color: string }[] = [
  { value: 'info', label: '信息', color: '#909399' },
  { value: 'warn', label: '告警', color: '#E6A23C' },
  { value: 'error', label: '故障', color: '#F56C6C' }
]
const eventDialog = ref(false)
const eventMode = ref<'create' | 'edit'>('create')
const editingEventIndex = ref(-1)
const eventForm = reactive<IotEvent>({
  identifier: '', name: '', type: 'info', outputParams: [], description: ''
})
const eventFormRef = ref()
const eventRules = {
  identifier: [
    { required: true, message: '请输入标识符', trigger: 'blur' },
    { pattern: /^[a-z][a-zA-Z0-9_]{0,31}$/, message: '小写字母开头', trigger: 'blur' }
  ],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }]
}

function openEventCreate() {
  eventMode.value = 'create'
  Object.assign(eventForm, { identifier: '', name: '', type: 'info', outputParams: [], description: '' })
  eventDialog.value = true
}
function openEventEdit(idx: number) {
  eventMode.value = 'edit'
  editingEventIndex.value = idx
  Object.assign(eventForm, JSON.parse(JSON.stringify(tsl.events[idx])))
  eventDialog.value = true
}
async function onEventSubmit() {
  if (!eventFormRef.value) return
  let valid = false
  try { valid = await eventFormRef.value.validate() } catch { valid = false }
  if (!valid) return
  const dup = tsl.events.findIndex((e, i) => e.identifier === eventForm.identifier && i !== editingEventIndex.value)
  if (dup >= 0) { ElMessage.error(`标识符「${eventForm.identifier}」已存在`); return }
  if (eventMode.value === 'create') {
    tsl.events.push({ ...eventForm, outputParams: [...eventForm.outputParams] })
    ElMessage.success('事件已添加')
  } else {
    tsl.events.splice(editingEventIndex.value, 1, { ...eventForm, outputParams: [...eventForm.outputParams] })
    ElMessage.success('事件已更新')
  }
  eventDialog.value = false
}
function onEventDelete(idx: number) {
  ElMessageBox.confirm(`确认删除事件「${tsl.events[idx].name}」?`, '删除确认', { type: 'warning' })
    .then(() => { tsl.events.splice(idx, 1); ElMessage.success('已删除') })
    .catch(() => {})
}

function addEventParam() {
  eventForm.outputParams.push({ identifier: '', name: '', type: 'string', required: false })
}
function removeEventParam(idx: number) {
  eventForm.outputParams.splice(idx, 1)
}

// ========== 服务编辑 ==========
const CALL_TYPES: { value: CallType; label: string }[] = [
  { value: 'async', label: '异步' },
  { value: 'sync', label: '同步' }
]
const serviceDialog = ref(false)
const serviceMode = ref<'create' | 'edit'>('create')
const editingServiceIndex = ref(-1)
const serviceForm = reactive<Service>({
  identifier: '', name: '', callType: 'async',
  inputParams: [], outputParams: [], description: ''
})
const serviceFormRef = ref()
const serviceRules = {
  identifier: [
    { required: true, message: '请输入标识符', trigger: 'blur' },
    { pattern: /^[a-z][a-zA-Z0-9_]{0,31}$/, message: '小写字母开头', trigger: 'blur' }
  ],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }]
}
function openServiceCreate() {
  serviceMode.value = 'create'
  Object.assign(serviceForm, { identifier: '', name: '', callType: 'async', inputParams: [], outputParams: [], description: '' })
  serviceDialog.value = true
}
function openServiceEdit(idx: number) {
  serviceMode.value = 'edit'
  editingServiceIndex.value = idx
  Object.assign(serviceForm, JSON.parse(JSON.stringify(tsl.services[idx])))
  serviceDialog.value = true
}
async function onServiceSubmit() {
  if (!serviceFormRef.value) return
  let valid = false
  try { valid = await serviceFormRef.value.validate() } catch { valid = false }
  if (!valid) return
  const dup = tsl.services.findIndex((s, i) => s.identifier === serviceForm.identifier && i !== editingServiceIndex.value)
  if (dup >= 0) { ElMessage.error(`标识符「${serviceForm.identifier}」已存在`); return }
  if (serviceMode.value === 'create') {
    tsl.services.push({
      ...serviceForm,
      inputParams: [...serviceForm.inputParams],
      outputParams: [...serviceForm.outputParams]
    })
    ElMessage.success('服务已添加')
  } else {
    tsl.services.splice(editingServiceIndex.value, 1, {
      ...serviceForm,
      inputParams: [...serviceForm.inputParams],
      outputParams: [...serviceForm.outputParams]
    })
    ElMessage.success('服务已更新')
  }
  serviceDialog.value = false
}
function onServiceDelete(idx: number) {
  ElMessageBox.confirm(`确认删除服务「${tsl.services[idx].name}」?`, '删除确认', { type: 'warning' })
    .then(() => { tsl.services.splice(idx, 1); ElMessage.success('已删除') })
    .catch(() => {})
}
function addParam(list: Param[]) {
  list.push({ identifier: '', name: '', type: 'string', required: false })
}
function removeParam(list: Param[], idx: number) {
  list.splice(idx, 1)
}

// ========== 保存 ==========
const saving = ref(false)
async function save() {
  if (!product.value) return
  saving.value = true
  try {
    await updateProduct({
      id: product.value.id,
      productKey: product.value.productKey,
      productName: product.value.productName,
      category: product.value.category,
      description: product.value.description,
      authType: product.value.authType,
      nodeType: product.value.nodeType,
      netType: product.value.netType,
      status: product.value.status,
      icon: product.value.icon,
      thingModel: tslJson.value
    })
    ElMessage.success('物模型已保存')
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

function goBack() { router.push('/product') }

onMounted(load)

// 当 TSL 变化时如果用户没保存,提示一下
let dirty = false
watch(tsl, () => { dirty = true }, { deep: true })
</script>

<template>
  <div
    v-loading="loading"
    class="page-container tsl-page"
  >
    <div class="tsl-header">
      <el-button
        :icon="ArrowLeft"
        @click="goBack"
      >
        返回产品列表
      </el-button>
      <h2
        v-if="product"
        class="page-title"
      >
        物模型 — {{ product.productName }}
        <el-tag
          size="small"
          type="info"
          style="margin-left:8px"
        >
          {{ product.productKey }}
        </el-tag>
      </h2>
      <div class="header-actions">
        <el-button
          type="primary"
          :loading="saving"
          @click="save"
        >
          保存物模型
        </el-button>
      </div>
    </div>

    <div class="tsl-layout">
      <!-- 左侧:编辑区 -->
      <div class="tsl-edit">
        <el-tabs
          v-model="activeTab"
          type="border-card"
        >
          <!-- 属性 -->
          <el-tab-pane
            label="属性 (properties)"
            name="properties"
          >
            <div class="tab-toolbar">
              <span class="hint">设备的实时数据点,如 温度、湿度、开关状态。设备主动上报,平台可选下发设置值。</span>
              <el-button
                type="primary"
                :icon="Plus"
                size="small"
                @click="openPropCreate"
              >
                新增属性
              </el-button>
            </div>
            <el-table
              :data="tsl.properties"
              border
              size="small"
              empty-text="暂无属性"
            >
              <el-table-column
                prop="identifier"
                label="标识符"
                width="140"
              />
              <el-table-column
                prop="name"
                label="名称"
                width="120"
              />
              <el-table-column
                label="类型"
                width="90"
              >
                <template #default="{ row }">
                  {{ row.type }}
                </template>
              </el-table-column>
              <el-table-column
                label="读写"
                width="80"
              >
                <template #default="{ row }">
                  <el-tag
                    size="small"
                    :type="row.accessMode === 'ro' ? 'info' : row.accessMode === 'rw' ? 'success' : 'warning'"
                  >
                    {{ row.accessMode }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column
                prop="unit"
                label="单位"
                width="80"
              />
              <el-table-column
                prop="description"
                label="描述"
                show-overflow-tooltip
              />
              <el-table-column
                label="操作"
                width="150"
                fixed="right"
              >
                <template #default="{ $index }">
                  <el-button
                    link
                    type="primary"
                    :icon="Edit"
                    @click="openPropEdit($index)"
                  >
                    编辑
                  </el-button>
                  <el-button
                    link
                    type="danger"
                    :icon="Delete"
                    @click="onPropDelete($index)"
                  >
                    删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <!-- 事件 -->
          <el-tab-pane
            :label="`事件 (events) - ${tsl.events.length}`"
            name="events"
          >
            <div class="tab-toolbar">
              <span class="hint">设备主动上报的事件,带输出参数。如 故障告警 {code, message}、按键触发 {key}。</span>
              <el-button
                type="primary"
                :icon="Plus"
                size="small"
                @click="openEventCreate"
              >
                新增事件
              </el-button>
            </div>
            <el-table
              :data="tsl.events"
              border
              size="small"
              empty-text="暂无事件"
            >
              <el-table-column
                prop="identifier"
                label="标识符"
                width="140"
              />
              <el-table-column
                prop="name"
                label="名称"
                width="120"
              />
              <el-table-column
                label="类型"
                width="80"
              >
                <template #default="{ row }">
                  <el-tag
                    size="small"
                    :style="{ color: ['#909399','#E6A23C','#F56C6C'][['info','warn','error'].indexOf(row.type)] }"
                  >
                    {{ ['信息','告警','故障'][['info','warn','error'].indexOf(row.type)] }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="输出参数">
                <template #default="{ row }">
                  <span v-if="row.outputParams?.length">
                    <el-tag
                      v-for="p in row.outputParams"
                      :key="p.identifier"
                      size="small"
                      style="margin-right:4px"
                    >
                      {{ p.identifier }}:{{ p.type }}
                    </el-tag>
                  </span>
                  <span
                    v-else
                    class="text-muted"
                  >无</span>
                </template>
              </el-table-column>
              <el-table-column
                label="操作"
                width="150"
                fixed="right"
              >
                <template #default="{ $index }">
                  <el-button
                    link
                    type="primary"
                    :icon="Edit"
                    @click="openEventEdit($index)"
                  >
                    编辑
                  </el-button>
                  <el-button
                    link
                    type="danger"
                    :icon="Delete"
                    @click="onEventDelete($index)"
                  >
                    删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <!-- 服务 -->
          <el-tab-pane
            :label="`服务 (services) - ${tsl.services.length}`"
            name="services"
          >
            <div class="tab-toolbar">
              <span class="hint">平台调用设备能力,如 远程重启、参数校准、固件升级。可带输入输出参数。</span>
              <el-button
                type="primary"
                :icon="Plus"
                size="small"
                @click="openServiceCreate"
              >
                新增服务
              </el-button>
            </div>
            <el-table
              :data="tsl.services"
              border
              size="small"
              empty-text="暂无服务"
            >
              <el-table-column
                prop="identifier"
                label="标识符"
                width="140"
              />
              <el-table-column
                prop="name"
                label="名称"
                width="120"
              />
              <el-table-column
                label="调用"
                width="80"
              >
                <template #default="{ row }">
                  <el-tag
                    size="small"
                    :type="row.callType === 'sync' ? 'success' : 'info'"
                  >
                    {{ row.callType }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column
                label="入参"
                width="200"
              >
                <template #default="{ row }">
                  <span v-if="row.inputParams?.length">
                    <el-tag
                      v-for="p in row.inputParams"
                      :key="p.identifier"
                      size="small"
                      style="margin-right:4px"
                    >
                      {{ p.identifier }}:{{ p.type }}
                    </el-tag>
                  </span>
                  <span
                    v-else
                    class="text-muted"
                  >无</span>
                </template>
              </el-table-column>
              <el-table-column
                label="出参"
                width="200"
              >
                <template #default="{ row }">
                  <span v-if="row.outputParams?.length">
                    <el-tag
                      v-for="p in row.outputParams"
                      :key="p.identifier"
                      size="small"
                      style="margin-right:4px"
                    >
                      {{ p.identifier }}:{{ p.type }}
                    </el-tag>
                  </span>
                  <span
                    v-else
                    class="text-muted"
                  >无</span>
                </template>
              </el-table-column>
              <el-table-column
                label="操作"
                width="150"
                fixed="right"
              >
                <template #default="{ $index }">
                  <el-button
                    link
                    type="primary"
                    :icon="Edit"
                    @click="openServiceEdit($index)"
                  >
                    编辑
                  </el-button>
                  <el-button
                    link
                    type="danger"
                    :icon="Delete"
                    @click="onServiceDelete($index)"
                  >
                    删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </div>

      <!-- 右侧:JSON 预览 -->
      <div class="tsl-preview">
        <div class="preview-header">
          <span><el-icon><View /></el-icon> TSL JSON 预览</span>
          <el-button
            :icon="DocumentCopy"
            size="small"
            link
            @click="copyJson"
          >
            复制
          </el-button>
        </div>
        <pre class="tsl-pre">{{ tslJson }}</pre>
      </div>
    </div>

    <!-- 属性编辑对话框 -->
    <el-dialog
      v-model="propDialog"
      :title="propMode === 'create' ? '新增属性' : '编辑属性'"
      width="600px"
      destroy-on-close
    >
      <el-form
        ref="propFormRef"
        :model="propForm"
        :rules="propRules"
        label-width="100px"
        @submit.prevent
      >
        <el-form-item
          label="标识符"
          prop="identifier"
        >
          <el-input
            v-model="propForm.identifier"
            placeholder="英文,如 temperature"
          />
          <div class="hint">
            设备上报和平台读取时使用的字段名,创建后不可改
          </div>
        </el-form-item>
        <el-form-item
          label="名称"
          prop="name"
        >
          <el-input
            v-model="propForm.name"
            placeholder="中文展示名,如 温度"
          />
        </el-form-item>
        <el-form-item label="数据类型">
          <el-select
            v-model="propForm.type"
            style="width: 200px"
          >
            <el-option
              v-for="t in PROPERTY_TYPES"
              :key="t.value"
              :label="t.label"
              :value="t.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="读写权限">
          <el-radio-group v-model="propForm.accessMode">
            <el-radio
              v-for="m in ACCESS_MODES"
              :key="m.value"
              :value="m.value"
            >
              {{ m.label }}<span
                class="hint"
                style="margin-left:4px"
              >({{ m.desc }})</span>
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="是否必填">
          <el-switch v-model="propForm.required" />
        </el-form-item>
        <el-form-item label="单位">
          <el-input
            v-model="propForm.unit"
            placeholder="如 ℃、%、V"
            style="width: 200px"
          />
        </el-form-item>

        <!-- 数值类型的范围 -->
        <template v-if="propForm.type === 'int' || propForm.type === 'float'">
          <el-form-item label="最小值">
            <el-input
              v-model="propForm.specs!.min"
              placeholder="可空"
              style="width: 200px"
            />
          </el-form-item>
          <el-form-item label="最大值">
            <el-input
              v-model="propForm.specs!.max"
              placeholder="可空"
              style="width: 200px"
            />
          </el-form-item>
          <el-form-item label="步长">
            <el-input
              v-model="propForm.specs!.step"
              placeholder="可空,如 0.1"
              style="width: 200px"
            />
          </el-form-item>
        </template>

        <!-- 枚举类型的选项 -->
        <template v-if="propForm.type === 'enum'">
          <el-form-item label="枚举选项">
            <div style="width: 100%">
              <div
                v-for="(o, idx) in propForm.specs?.options"
                :key="idx"
                class="enum-row"
              >
                <el-tag
                  closable
                  @close="removeEnumOption(idx)"
                >
                  {{ o.value }} : {{ o.label }}
                </el-tag>
              </div>
              <div
                class="enum-row"
                style="margin-top: 8px"
              >
                <el-input
                  v-model="enumInput"
                  placeholder="格式: 值:标签,如 0:关闭"
                  style="width: 240px"
                  @keyup.enter="addEnumOption"
                />
                <el-button
                  :icon="Plus"
                  @click="addEnumOption"
                >
                  添加
                </el-button>
              </div>
            </div>
          </el-form-item>
        </template>

        <el-form-item label="描述">
          <el-input
            v-model="propForm.description"
            type="textarea"
            :rows="2"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="propDialog = false">
          取消
        </el-button>
        <el-button
          type="primary"
          @click="onPropSubmit"
        >
          保存
        </el-button>
      </template>
    </el-dialog>

    <!-- 事件编辑对话框 -->
    <el-dialog
      v-model="eventDialog"
      :title="eventMode === 'create' ? '新增事件' : '编辑事件'"
      width="640px"
      destroy-on-close
    >
      <el-form
        ref="eventFormRef"
        :model="eventForm"
        :rules="eventRules"
        label-width="100px"
        @submit.prevent
      >
        <el-form-item
          label="标识符"
          prop="identifier"
        >
          <el-input
            v-model="eventForm.identifier"
            placeholder="英文,如 high_temp"
          />
        </el-form-item>
        <el-form-item
          label="名称"
          prop="name"
        >
          <el-input
            v-model="eventForm.name"
            placeholder="中文,如 高温告警"
          />
        </el-form-item>
        <el-form-item label="事件类型">
          <el-radio-group v-model="eventForm.type">
            <el-radio
              v-for="t in EVENT_TYPES"
              :key="t.value"
              :value="t.value"
            >
              <span :style="{ color: t.color }">{{ t.label }}</span>
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="输出参数">
          <div style="width: 100%">
            <div
              v-for="(p, idx) in eventForm.outputParams"
              :key="idx"
              class="param-row"
            >
              <el-input
                v-model="p.identifier"
                placeholder="标识符"
                style="width: 140px"
                size="small"
              />
              <el-input
                v-model="p.name"
                placeholder="名称"
                style="width: 140px"
                size="small"
              />
              <el-select
                v-model="p.type"
                size="small"
                style="width: 120px"
              >
                <el-option
                  v-for="t in PROPERTY_TYPES.filter(x => x.value !== 'date')"
                  :key="t.value"
                  :label="t.value"
                  :value="t.value"
                />
              </el-select>
              <el-switch v-model="p.required" />
              <el-button
                :icon="Delete"
                size="small"
                link
                @click="removeEventParam(idx)"
              />
            </div>
            <el-button
              :icon="Plus"
              size="small"
              style="margin-top: 8px"
              @click="addEventParam"
            >
              添加参数
            </el-button>
          </div>
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="eventForm.description"
            type="textarea"
            :rows="2"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="eventDialog = false">
          取消
        </el-button>
        <el-button
          type="primary"
          @click="onEventSubmit"
        >
          保存
        </el-button>
      </template>
    </el-dialog>

    <!-- 服务编辑对话框 -->
    <el-dialog
      v-model="serviceDialog"
      :title="serviceMode === 'create' ? '新增服务' : '编辑服务'"
      width="720px"
      destroy-on-close
    >
      <el-form
        ref="serviceFormRef"
        :model="serviceForm"
        :rules="serviceRules"
        label-width="100px"
        @submit.prevent
      >
        <el-form-item
          label="标识符"
          prop="identifier"
        >
          <el-input
            v-model="serviceForm.identifier"
            placeholder="英文,如 reboot"
          />
        </el-form-item>
        <el-form-item
          label="名称"
          prop="name"
        >
          <el-input
            v-model="serviceForm.name"
            placeholder="中文,如 远程重启"
          />
        </el-form-item>
        <el-form-item label="调用方式">
          <el-radio-group v-model="serviceForm.callType">
            <el-radio
              v-for="t in CALL_TYPES"
              :key="t.value"
              :value="t.value"
            >
              {{ t.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="输入参数">
          <div style="width: 100%">
            <div
              v-for="(p, idx) in serviceForm.inputParams"
              :key="idx"
              class="param-row"
            >
              <el-input
                v-model="p.identifier"
                placeholder="标识符"
                style="width: 140px"
                size="small"
              />
              <el-input
                v-model="p.name"
                placeholder="名称"
                style="width: 140px"
                size="small"
              />
              <el-select
                v-model="p.type"
                size="small"
                style="width: 120px"
              >
                <el-option
                  v-for="t in PROPERTY_TYPES.filter(x => x.value !== 'date')"
                  :key="t.value"
                  :label="t.value"
                  :value="t.value"
                />
              </el-select>
              <el-switch v-model="p.required" />
              <el-button
                :icon="Delete"
                size="small"
                link
                @click="removeParam(serviceForm.inputParams, idx)"
              />
            </div>
            <el-button
              :icon="Plus"
              size="small"
              style="margin-top: 8px"
              @click="addParam(serviceForm.inputParams)"
            >
              添加输入参数
            </el-button>
          </div>
        </el-form-item>
        <el-form-item label="输出参数">
          <div style="width: 100%">
            <div
              v-for="(p, idx) in serviceForm.outputParams"
              :key="idx"
              class="param-row"
            >
              <el-input
                v-model="p.identifier"
                placeholder="标识符"
                style="width: 140px"
                size="small"
              />
              <el-input
                v-model="p.name"
                placeholder="名称"
                style="width: 140px"
                size="small"
              />
              <el-select
                v-model="p.type"
                size="small"
                style="width: 120px"
              >
                <el-option
                  v-for="t in PROPERTY_TYPES.filter(x => x.value !== 'date')"
                  :key="t.value"
                  :label="t.value"
                  :value="t.value"
                />
              </el-select>
              <el-switch v-model="p.required" />
              <el-button
                :icon="Delete"
                size="small"
                link
                @click="removeParam(serviceForm.outputParams, idx)"
              />
            </div>
            <el-button
              :icon="Plus"
              size="small"
              style="margin-top: 8px"
              @click="addParam(serviceForm.outputParams)"
            >
              添加输出参数
            </el-button>
          </div>
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="serviceForm.description"
            type="textarea"
            :rows="2"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="serviceDialog = false">
          取消
        </el-button>
        <el-button
          type="primary"
          @click="onServiceSubmit"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.tsl-page {
  .tsl-header {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 16px;
    .page-title { margin: 0; flex: 1; }
    .header-actions { display: flex; gap: 8px; }
  }
}
.tsl-layout {
  display: grid;
  grid-template-columns: 1fr 460px;
  gap: 16px;
  align-items: start;
}
.tsl-edit {
  background: #fff;
  border-radius: 8px;
  :deep(.el-tabs__content) { padding: 0; }
  :deep(.el-tab-pane) { padding: 12px; }
}
.tsl-preview {
  background: #fff;
  border-radius: 8px;
  padding: 12px;
  position: sticky;
  top: 16px;
  max-height: calc(100vh - 120px);
  display: flex;
  flex-direction: column;
  .preview-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;
    font-weight: 500;
    span { display: flex; align-items: center; gap: 4px; }
  }
  .tsl-pre {
    background: #1e1e1e;
    color: #d4d4d4;
    padding: 12px;
    border-radius: 6px;
    font-family: 'Menlo', 'Consolas', monospace;
    font-size: 12px;
    line-height: 1.5;
    flex: 1;
    overflow: auto;
    margin: 0;
  }
}
.tab-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  .hint { color: #909399; font-size: 12px; flex: 1; }
}
.hint { color: #909399; font-size: 12px; margin-top: 4px; }
.text-muted { color: #c0c4cc; font-size: 12px; }
.enum-row { display: flex; gap: 8px; align-items: center; }
.param-row { display: flex; gap: 8px; align-items: center; margin-bottom: 8px; }

@media (max-width: 1100px) {
  .tsl-layout { grid-template-columns: 1fr; }
  .tsl-preview { position: static; max-height: 400px; }
}
</style>