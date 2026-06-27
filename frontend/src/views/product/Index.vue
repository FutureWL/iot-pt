<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Search, Refresh, View, Setting, ArrowDown } from '@element-plus/icons-vue'
import {
  pageProducts,
  createProduct,
  updateProduct,
  deleteProduct,
  defaultThingModel,
  getProduct,
  type IotProductVO,
  type IotProductDTO,
  type IotProductQuery
} from '@/api/iot/product'

// ========== 列表 ==========
const query = reactive<IotProductQuery>({
  pageNum: 1, pageSize: 10, keyword: '', category: '', netType: '', status: undefined
})
const loading = ref(false)
const list = ref<IotProductVO[]>([])
const total = ref(0)

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

async function load() {
  loading.value = true
  try {
    const res: any = await pageProducts(query)
    list.value = res.data.records ?? []
    total.value = res.data.total ?? 0
  } finally {
    loading.value = false
  }
}
function onSearch() { query.pageNum = 1; load() }
function onReset() {
  query.keyword = ''; query.category = ''; query.netType = ''; query.status = undefined
  query.pageNum = 1; load()
}
function onPageChange(p: number) { query.pageNum = p; load() }
function onSizeChange(s: number) { query.pageSize = s; query.pageNum = 1; load() }

// ========== 新建/编辑对话框 ==========
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const submitting = ref(false)
const formRef = ref()
const form = reactive<IotProductDTO>({
  id: undefined,
  productKey: '',
  productName: '',
  category: '',
  description: '',
  authType: 'deviceSecret',
  nodeType: 0,
  netType: 'MQTT',
  status: 1,
  thingModel: ''
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
    load()
  } catch (e) {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}

async function onDelete(row: IotProductVO) {
  await ElMessageBox.confirm(`确认删除产品「${row.productName}」?该产品下的设备会失去物模型定义`, '删除确认', {
    type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
  })
  try {
    await deleteProduct(row.id)
    ElMessage.success('删除成功')
    load()
  } catch {}
}

// ========== 物模型编辑器跳转 ==========
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

// 只读预览(用 dialog 弹 JSON)
const tslVisible = ref(false)
const tslContent = ref('')
async function previewTsl(row: IotProductVO) {
  const res: any = await getProduct(row.id)
  tslContent.value = res.data.thingModel
  tslVisible.value = true
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">产品管理</h2>

    <div class="page-card search-bar">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="Key / 名称 / 描述" clearable style="width: 220px"
            @keyup.enter="onSearch" />
        </el-form-item>
        <el-form-item label="联网方式">
          <el-select v-model="query.netType" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="o in netTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
          <el-button :icon="Refresh" @click="onReset">重置</el-button>
          <el-button type="success" :icon="Plus" @click="openCreate">新建产品</el-button>
          <el-dropdown trigger="click" @command="useTemplate">
            <el-button type="primary" plain>
              从模板新建<el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-for="t in PRODUCT_TEMPLATES" :key="t.key" :command="t">
                  <div style="font-weight: 500;">{{ t.name }}</div>
                  <div style="font-size: 11px; color: #909399;">{{ t.description }}</div>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </el-form-item>
      </el-form>
    </div>

    <div class="page-card">
      <el-table v-loading="loading" :data="list" stripe border>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="productKey" label="产品 Key" min-width="160">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.productKey }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="productName" label="产品名称" min-width="160" />
        <el-table-column prop="category" label="分类" min-width="100" />
        <el-table-column label="节点类型" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ nodeTypeLabel(row.nodeType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="netType" label="联网" width="90" />
        <el-table-column label="认证" width="110">
          <template #default="{ row }">
            <el-tag size="small" type="warning">{{ authTypeLabel(row.authType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-button link type="success" :icon="Setting" @click="openThingModel(row)">物模型</el-button>
            <el-button link type="info" :icon="View" @click="previewTsl(row)">预览</el-button>
            <el-button link type="danger" :icon="Delete" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无产品" /></template>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="onPageChange"
          @size-change="onSizeChange" />
      </div>
    </div>

    <!-- 新建/编辑对话框 -->
    <el-dialog v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新建产品' : '编辑产品'"
      width="640px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" @submit.prevent>
        <el-form-item label="产品 Key" prop="productKey">
          <el-input v-model="form.productKey" placeholder="英文,设备上报会用,如 th_sensor" />
        </el-form-item>
        <el-form-item label="产品名称" prop="productName">
          <el-input v-model="form.productName" placeholder="中文展示名,如 温湿度传感器" />
        </el-form-item>
        <el-form-item label="分类">
          <el-input v-model="form.category" placeholder="如 传感器 / 执行器 / 网关" />
        </el-form-item>
        <el-form-item label="节点类型">
          <el-radio-group v-model="form.nodeType">
            <el-radio v-for="o in nodeTypeOptions" :key="o.value" :value="o.value">{{ o.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="联网方式" prop="netType">
          <el-radio-group v-model="form.netType">
            <el-radio v-for="o in netTypeOptions" :key="o.value" :value="o.value">{{ o.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="认证方式" prop="authType">
          <el-select v-model="form.authType" style="width: 220px">
            <el-option v-for="o in authTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="物模型 JSON">
          <el-input v-model="form.thingModel" type="textarea" :rows="6"
            placeholder='{"properties":[],"events":[],"services":[]}' />
          <div class="hint">P1-② 阶段会提供可视化编辑器,目前先手填 JSON</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">
          {{ dialogMode === 'create' ? '创建' : '保存' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 物模型预览 -->
    <el-dialog v-model="tslVisible" title="物模型 (TSL)" width="640px">
      <pre class="tsl-pre">{{ tslContent }}</pre>
      <template #footer>
        <el-button @click="tslVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.search-bar { margin-bottom: 12px; padding: 16px; :deep(.el-form-item) { margin-bottom: 0; } }
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 16px; }
.hint { font-size: 12px; color: #909399; margin-top: 4px; }
.tsl-pre {
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 16px;
  border-radius: 6px;
  font-family: 'Menlo', 'Consolas', monospace;
  font-size: 12px;
  line-height: 1.6;
  max-height: 60vh;
  overflow: auto;
}
</style>