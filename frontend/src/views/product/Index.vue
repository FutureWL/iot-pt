<script setup lang="ts">
/**
 * 产品管理 — <CrudList> 重构版 (673 → ~350 行)
 *
 * 设计要点:
 *   - 列表/筛选/分页 由 <CrudList> 接管
 *   - "新建/编辑"对话框保留(物模型 JSON + 国网芯模板)
 *   - "从模板新建"下拉菜单保留
 *   - 物模型预览对话框保留
 *   - 状态/认证/联网 等标签用 StatusTag 统一
 */
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus, Edit, Delete, View, Setting, ArrowDown
} from '@element-plus/icons-vue'
import {
  productCrud,
  createProduct,
  updateProduct,
  defaultThingModel,
  getProduct,
  type IotProductVO,
  type IotProductDTO,
  type IotProductQuery
} from '@/api/iot/product'
import { CrudList, StatusTag, type ColumnDef, type FilterItem, type StatusType } from '@/ui'

// ========== 列表 ==========
const netTypeOptions = [
  { label: 'MQTT', value: 'MQTT' },
  { label: 'TCP', value: 'TCP' }
]
const authTypeOptions = [
  { label: '设备密钥', value: 'deviceSecret' },
  { label: '动态注册', value: 'dynamic' },
  { label: '免认证', value: 'none' }
]
const nodeTypeOptions = [
  { label: '直连设备', value: 0 },
  { label: '网关', value: 1 },
  { label: '网关子设备', value: 2 }
]
const nodeTypeLabel = (n?: number) => nodeTypeOptions.find(o => o.value === n)?.label ?? '-'
const authTypeLabel = (a?: string) => authTypeOptions.find(o => o.value === a)?.label ?? '-'

const filters: FilterItem[] = [
  {
    prop: 'netType',
    label: '联网方式',
    type: 'select',
    options: netTypeOptions
  },
  {
    prop: 'status',
    label: '状态',
    type: 'select',
    options: [
      { label: '启用', value: 1 },
      { label: '禁用', value: 0 }
    ]
  }
]

const columns: ColumnDef<IotProductVO>[] = [
  { prop: 'id', label: 'ID', width: 80 },
  { prop: 'productKey', label: '产品 Key', minWidth: 160, slot: 'productKey' },
  { prop: 'productName', label: '产品名称', minWidth: 160 },
  { prop: 'category', label: '分类', minWidth: 100 },
  { prop: 'nodeType', label: '节点类型', width: 120, slot: 'nodeType' },
  { prop: 'netType', label: '联网', width: 90 },
  { prop: 'authType', label: '认证', width: 110, slot: 'auth' },
  { prop: 'description', label: '描述', minWidth: 200, showOverflowTooltip: true },
  { prop: 'status', label: '状态', width: 80, slot: 'status' },
  { prop: 'createdAt', label: '创建时间', width: 170 },
  { label: '操作', width: 320, fixed: 'right', slot: 'actions' }
]

const STATUS_TYPE_MAP: Record<string, StatusType> = { '0': 'info', '1': 'success' }
const STATUS_LABEL_MAP: Record<number, string> = { 0: '禁用', 1: '启用' }

const crudListRef = ref<{ refresh: () => Promise<void> } | null>(null)
function refresh(): void { void crudListRef.value?.refresh() }

// ========== 新建/编辑对话框 ==========
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const submitting = ref(false)
const formRef = ref()
const form = reactive<IotProductDTO>({
  id: undefined, productKey: '', productName: '', category: '', description: '',
  authType: 'deviceSecret', nodeType: 0, netType: 'MQTT', status: 1, thingModel: ''
})

const rules = {
  productKey: [
    { required: true, message: '请输入产品 Key', trigger: 'blur' },
    { pattern: /^[A-Za-z][A-Za-z0-9_-]{1,31}$/, message: '以字母开头,只能含字母数字下划线短横线', trigger: 'blur' }
  ],
  productName: [{ required: true, message: '请输入产品名称', trigger: 'blur' }],
  authType: [{ required: true, message: '请选择认证方式', trigger: 'change' }],
  netType: [{ required: true, message: '请选择联网方式', trigger: 'change' }]
}

async function openCreate() {
  dialogMode.value = 'create'
  const res: any = await defaultThingModel()
  Object.assign(form, {
    id: undefined, productKey: '', productName: '', category: '', description: '',
    authType: 'deviceSecret', nodeType: 0, netType: 'MQTT', status: 1,
    thingModel: res.data ?? '{"properties":[],"events":[],"services":[]}'
  })
  dialogVisible.value = true
}

async function openEdit(row: IotProductVO) {
  const res: any = await getProduct(row.id)
  const p = res.data
  Object.assign(form, {
    id: p.id, productKey: p.productKey, productName: p.productName,
    category: p.category ?? '', description: p.description ?? '',
    authType: p.authType, nodeType: p.nodeType, netType: p.netType,
    status: p.status, icon: p.icon ?? '',
    thingModel: p.thingModel ?? '{"properties":[],"events":[],"services":[]}'
  })
  dialogMode.value = 'edit'
  dialogVisible.value = true
}

async function onSubmit() {
  if (!formRef.value) return
  // eslint-disable-next-line no-useless-assignment
  let valid = false
  try { valid = await formRef.value.validate() } catch { valid = false }
  if (!valid) return
  submitting.value = true
  try {
    if (dialogMode.value === 'create') {
      await createProduct(form)
      ElMessage.success('创建成功')
    } else {
      await updateProduct(form)
      ElMessage.success('更新成功')
    }
    dialogVisible.value = false
    refresh()
  } catch { /* 拦截器已提示 */ } finally { submitting.value = false }
}

async function onDelete(row: IotProductVO) {
  await ElMessageBox.confirm(`确认删除产品「${row.productName}」?该产品下的设备会失去物模型定义`, '删除确认', {
    type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
  })
  try {
    await productCrud.remove!(row.id)
    ElMessage.success('删除成功')
    refresh()
  } catch { /* ignore */ }
}

const router = useRouter()
function openThingModel(row: IotProductVO) {
  router.push(`/product/thing-model/${row.id}`)
}

// ========== 国网芯产品模板(P1 蓝图扩展) ==========
interface ProductTemplate {
  key: string
  name: string
  category: string
  description: string
  nodeType: number
  netType: string
  authType: string
  thingModel: string
}

const PRODUCT_TEMPLATES: ProductTemplate[] = [
  {
    key: 'sg_pd_monitor',
    name: '国网芯局放监测终端',
    category: '在线监测装置',
    description: 'UHF + HFCT 双通道局放监测,内置阈值告警',
    nodeType: 0, netType: 'MQTT', authType: 'deviceSecret',
    thingModel: JSON.stringify({
      properties: [
        { identifier: 'dischargeAmplitude', name: '局放幅值', type: 'double', specs: { unit: 'pC', min: 0, max: 5000, step: 0.1 }, accessMode: 'ro' },
        { identifier: 'pulseCount', name: '脉冲计数', type: 'int', specs: { unit: '个', min: 0, max: 999999 }, accessMode: 'ro' },
        { identifier: 'phaseAngle', name: '相位', type: 'double', specs: { unit: '°', min: 0, max: 360 }, accessMode: 'ro' },
        { identifier: 'channelType', name: '通道类型', type: 'enum', specs: { 0: 'UHF', 1: 'HFCT' }, accessMode: 'ro' }
      ],
      events: [
        { identifier: 'pd_alarm', name: '局放告警', type: 'alert', params: [{ identifier: 'amplitude', type: 'double' }, { identifier: 'level', type: 'enum' }] }
      ],
      services: [
        { identifier: 'set_threshold', name: '设置告警阈值', params: [{ identifier: 'value', type: 'double' }] }
      ]
    }, null, 2)
  },
  {
    key: 'sg_temp_sensor',
    name: '国网芯温度传感器',
    category: '在线监测装置',
    description: '母排/触头/电缆接头无线温度监测',
    nodeType: 0, netType: 'MQTT', authType: 'deviceSecret',
    thingModel: JSON.stringify({
      properties: [
        { identifier: 'temperature', name: '温度', type: 'double', specs: { unit: '℃', min: -40, max: 200, step: 0.1 }, accessMode: 'ro' },
        { identifier: 'batteryLevel', name: '电量', type: 'int', specs: { unit: '%', min: 0, max: 100 }, accessMode: 'ro' },
        { identifier: 'location', name: '安装位置', type: 'enum', specs: { 0: '母排', 1: '触头', 2: '电缆接头' }, accessMode: 'rw' }
      ],
      events: [
        { identifier: 'over_temp', name: '超温告警', type: 'alert', params: [{ identifier: 'temperature', type: 'double' }] }
      ],
      services: []
    }, null, 2)
  },
  {
    key: 'sg_env_monitor',
    name: '国网芯柜内环境监测',
    category: '在线监测装置',
    description: '柜内微环境(温湿度/水浸/倾角/振动)',
    nodeType: 0, netType: 'MQTT', authType: 'deviceSecret',
    thingModel: JSON.stringify({
      properties: [
        { identifier: 'temperature', name: '柜内温度', type: 'double', specs: { unit: '℃' }, accessMode: 'ro' },
        { identifier: 'humidity', name: '柜内湿度', type: 'double', specs: { unit: '%' }, accessMode: 'ro' },
        { identifier: 'waterStatus', name: '水浸状态', type: 'enum', specs: { 0: '正常', 1: '水浸' }, accessMode: 'ro' },
        { identifier: 'tiltAngle', name: '倾角', type: 'double', specs: { unit: '°' }, accessMode: 'ro' },
        { identifier: 'vibrationRMS', name: '振动 RMS', type: 'double', specs: { unit: 'g' }, accessMode: 'ro' }
      ],
      events: [
        { identifier: 'water_alarm', name: '水浸告警', type: 'alert' },
        { identifier: 'condensation', name: '凝露预警', type: 'alert' }
      ],
      services: []
    }, null, 2)
  },
  {
    key: 'sg_gateway',
    name: '国网芯边缘网关',
    category: '边缘网关',
    description: '汇聚多台监测装置数据,通过 MQTT/4G 上行',
    nodeType: 1, netType: 'MQTT', authType: 'deviceSecret',
    thingModel: JSON.stringify({
      properties: [
        { identifier: 'onlineCount', name: '在线子设备数', type: 'int', accessMode: 'ro' },
        { identifier: 'uplinkRssi', name: '上行信号强度', type: 'int', specs: { unit: 'dBm' }, accessMode: 'ro' }
      ],
      events: [],
      services: [
        { identifier: 'add_sub_device', name: '添加子设备', params: [{ identifier: 'subDeviceKey', type: 'string' }] }
      ]
    }, null, 2)
  }
]

function useTemplate(t: ProductTemplate) {
  dialogMode.value = 'create'
  Object.assign(form, {
    id: undefined, productKey: t.key, productName: t.name, category: t.category,
    description: t.description, authType: t.authType, nodeType: t.nodeType,
    netType: t.netType, status: 1, thingModel: t.thingModel
  })
  dialogVisible.value = true
}

const tslVisible = ref(false)
const tslContent = ref('')
async function previewTsl(row: IotProductVO) {
  const res: any = await getProduct(row.id)
  tslContent.value = res.data.thingModel
  tslVisible.value = true
}
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">
      产品管理
    </h2>

    <CrudList
      ref="crudListRef"
      :api="productCrud"
      :columns="columns"
      :filters="filters"
      :row-key="'id'"
      empty-text="暂无产品"
      keyword-placeholder="Key / 名称 / 描述"
    >
      <template #toolbar>
        <el-button
          type="success"
          :icon="Plus"
          @click="openCreate"
        >
          新建产品
        </el-button>
        <el-dropdown
          trigger="click"
          @command="useTemplate"
        >
          <el-button
            type="primary"
            plain
          >
            从模板新建<el-icon class="el-icon--right">
              <ArrowDown />
            </el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item
                v-for="t in PRODUCT_TEMPLATES"
                :key="t.key"
                :command="t"
              >
                <div style="font-weight: 500;">
                  {{ t.name }}
                </div>
                <div style="font-size: 11px; color: #909399;">
                  {{ t.description }}
                </div>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </template>

      <template #column-productKey="{ row }">
        <el-tag size="small" type="info">
          {{ (row as IotProductVO).productKey }}
        </el-tag>
      </template>

      <template #column-nodeType="{ row }">
        <el-tag size="small">
          {{ nodeTypeLabel((row as IotProductVO).nodeType) }}
        </el-tag>
      </template>

      <template #column-auth="{ row }">
        <el-tag size="small" type="warning">
          {{ authTypeLabel((row as IotProductVO).authType) }}
        </el-tag>
      </template>

      <template #column-status="{ row }">
        <StatusTag
          :value="(row as IotProductVO).status"
          :label="STATUS_LABEL_MAP[(row as IotProductVO).status]"
          :type-map="STATUS_TYPE_MAP"
        />
      </template>

      <template #column-actions="{ row }">
        <el-button
          link
          type="primary"
          :icon="Edit"
          @click="openEdit(row as IotProductVO)"
        >
          编辑
        </el-button>
        <el-button
          link
          type="success"
          :icon="Setting"
          @click="openThingModel(row as IotProductVO)"
        >
          物模型
        </el-button>
        <el-button
          link
          type="info"
          :icon="View"
          @click="previewTsl(row as IotProductVO)"
        >
          预览
        </el-button>
        <el-button
          link
          type="danger"
          :icon="Delete"
          @click="onDelete(row as IotProductVO)"
        >
          删除
        </el-button>
      </template>
    </CrudList>

    <!-- 新建/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新建产品' : '编辑产品'"
      width="640px"
      destroy-on-close
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
        @submit.prevent
      >
        <el-form-item label="产品 Key" prop="productKey">
          <el-input
            v-model="form.productKey"
            placeholder="英文,设备上报会用,如 th_sensor"
          />
        </el-form-item>
        <el-form-item label="产品名称" prop="productName">
          <el-input
            v-model="form.productName"
            placeholder="中文展示名,如 温湿度传感器"
          />
        </el-form-item>
        <el-form-item label="分类">
          <el-input
            v-model="form.category"
            placeholder="如 传感器 / 执行器 / 网关"
          />
        </el-form-item>
        <el-form-item label="节点类型">
          <el-radio-group v-model="form.nodeType">
            <el-radio
              v-for="o in nodeTypeOptions"
              :key="o.value"
              :value="o.value"
            >
              {{ o.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="联网方式" prop="netType">
          <el-radio-group v-model="form.netType">
            <el-radio
              v-for="o in netTypeOptions"
              :key="o.value"
              :value="o.value"
            >
              {{ o.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="认证方式" prop="authType">
          <el-select
            v-model="form.authType"
            style="width: 220px"
          >
            <el-option
              v-for="o in authTypeOptions"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
          />
        </el-form-item>
        <el-form-item label="物模型 JSON">
          <el-input
            v-model="form.thingModel"
            type="textarea"
            :rows="6"
            placeholder='{"properties":[],"events":[],"services":[]}'
          />
          <div class="hint">
            P1-② 阶段会提供可视化编辑器,目前先手填 JSON
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="onSubmit"
        >
          {{ dialogMode === 'create' ? '创建' : '保存' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 物模型预览 -->
    <el-dialog
      v-model="tslVisible"
      title="物模型 (TSL)"
      width="640px"
    >
      <pre class="tsl-pre">{{ tslContent }}</pre>
      <template #footer>
        <el-button @click="tslVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.hint {
  font-size: $font-size-extra-small;
  color: var(--iot-text-secondary);
  margin-top: $spacing-4;
}
.tsl-pre {
  background: #1e1e1e;
  color: #d4d4d4;
  padding: $spacing-16;
  border-radius: $radius-base;
  font-family: var(--iot-font-family-code);
  font-size: $font-size-extra-small;
  line-height: $line-height-base;
  max-height: 60vh;
  overflow: auto;
}
</style>