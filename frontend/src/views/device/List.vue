<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Search, Refresh, Key, View, Connection, Document } from '@element-plus/icons-vue'
import {
  pageDevices,
  createDevice,
  updateDevice,
  deleteDevice,
  resetDeviceSecret,
  toggleDeviceStatus,
  getDevice,
  type IotDeviceVO,
  type IotDeviceDTO,
  type IotDeviceQuery
} from '@/api/iot/device'
import { allProducts, type IotProductVO } from '@/api/iot/product'
import { allGroups, type IotDeviceGroupVO } from '@/api/iot/deviceGroup'

const router = useRouter()

// ========== 列表 ==========
const query = reactive<IotDeviceQuery>({
  pageNum: 1, pageSize: 10, keyword: '',
  productId: undefined, groupId: undefined, status: undefined
})
const loading = ref(false)
const list = ref<IotDeviceVO[]>([])
const total = ref(0)
const productOptions = ref<IotProductVO[]>([])
const groupOptions = ref<IotDeviceGroupVO[]>([])

const statusMap: Record<number, { label: string; type: string }> = {
  0: { label: '离线', type: 'info' },
  1: { label: '在线', type: 'success' },
  2: { label: '禁用', type: 'danger' }
}

async function load() {
  loading.value = true
  try {
    const res: any = await pageDevices(query)
    list.value = res.data.records ?? []
    total.value = res.data.total ?? 0
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  const [pRes, gRes]: any[] = await Promise.all([allProducts(), allGroups()])
  productOptions.value = pRes.data ?? []
  groupOptions.value = gRes.data ?? []
}

function onSearch() { query.pageNum = 1; load() }
function onReset() {
  query.keyword = ''; query.productId = undefined; query.groupId = undefined; query.status = undefined
  query.pageNum = 1; load()
}
function onPageChange(p: number) { query.pageNum = p; load() }
function onSizeChange(s: number) { query.pageSize = s; query.pageNum = 1; load() }

// ========== 新建/编辑对话框 ==========
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const submitting = ref(false)
const formRef = ref()
const form = reactive<IotDeviceDTO>({
  id: undefined, productId: undefined as any, deviceKey: '', deviceName: '',
  deviceSecret: '', groupId: undefined, location: '', description: ''
})

const rules = {
  productId: [{ required: true, message: '请选择产品', trigger: 'change' }],
  deviceKey: [
    { required: true, message: '请输入设备 Key', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_:-]{2,64}$/, message: '字母数字下划线短横线冒号', trigger: 'blur' }
  ],
  deviceName: [{ required: true, message: '请输入设备名', trigger: 'blur' }]
}

async function openCreate() {
  dialogMode.value = 'create'
  Object.assign(form, {
    id: undefined, productId: undefined, deviceKey: '', deviceName: '',
    deviceSecret: '', groupId: undefined, location: '', description: ''
  })
  dialogVisible.value = true
}

async function openEdit(row: IotDeviceVO) {
  const res: any = await getDevice(row.id, true)
  const d = res.data
  Object.assign(form, {
    id: d.id, productId: d.productId, deviceKey: d.deviceKey, deviceName: d.deviceName,
    deviceSecret: d.deviceSecret, groupId: d.groupId || undefined, location: d.location ?? '',
    description: d.description ?? ''
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
      const res: any = await createDevice(form)
      ElMessageBox.alert(
        `设备已创建!完整密钥(只显示一次):<br/><b>${res.data.deviceSecret}</b>`,
        '请保存设备密钥', { dangerouslyUseHTMLString: true, confirmButtonText: '已复制/已记住' }
      )
      dialogVisible.value = false
      load()
    } else {
      await updateDevice(form)
      ElMessage.success('更新成功')
      dialogVisible.value = false
      load()
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}

// ========== 删除 / 启停 / 重置密钥 ==========
async function onDelete(row: IotDeviceVO) {
  await ElMessageBox.confirm(`确认删除设备「${row.deviceName}」?`, '删除确认', {
    type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
  })
  try {
    await deleteDevice(row.id)
    ElMessage.success('删除成功')
    load()
  } catch {}
}

async function onResetSecret(row: IotDeviceVO) {
  await ElMessageBox.confirm(
    `重置后密钥会失效,需要重新烧录到设备。确认重置「${row.deviceName}」的密钥?`,
    '重置密钥', { type: 'warning' }
  )
  try {
    const res: any = await resetDeviceSecret(row.id)
    ElMessageBox.alert(`新密钥:<br/><b>${res.data}</b>`, '请保存', {
      dangerouslyUseHTMLString: true
    })
    load()
  } catch {}
}

async function onToggleStatus(row: IotDeviceVO) {
  const next = row.status === 2 ? 0 : 2
  await ElMessageBox.confirm(
    `${row.status === 2 ? '启用' : '禁用'}设备「${row.deviceName}」?`,
    '提示', { type: 'warning' }
  )
  try {
    await toggleDeviceStatus(row.id, next)
    ElMessage.success('操作成功')
    load()
  } catch {}
}

// ========== 跳转到影子 ==========
function openShadow(row: IotDeviceVO) {
  router.push(`/device/shadow?deviceId=${row.id}`)
}

onMounted(() => { loadOptions(); load() })
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">设备列表</h2>

    <div class="page-card search-bar">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="Key / 名称 / 描述" clearable style="width: 220px"
            @keyup.enter="onSearch" />
        </el-form-item>
        <el-form-item label="产品">
          <el-select v-model="query.productId" placeholder="全部" clearable style="width: 180px">
            <el-option v-for="p in productOptions" :key="p.id" :label="`${p.productKey} - ${p.productName}`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="分组">
          <el-select v-model="query.groupId" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="g in groupOptions" :key="g.id" :label="g.groupName" :value="g.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="离线" :value="0" />
            <el-option label="在线" :value="1" />
            <el-option label="禁用" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
          <el-button :icon="Refresh" @click="onReset">重置</el-button>
          <el-button type="success" :icon="Plus" @click="openCreate">新建设备</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="page-card">
      <el-table v-loading="loading" :data="list" stripe border>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="deviceKey" label="设备 Key" min-width="140">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.deviceKey }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="deviceName" label="设备名" min-width="180" />
        <el-table-column label="所属产品" min-width="180">
          <template #default="{ row }">
            <span class="link-like" @click="query.productId = row.productId; onSearch()">
              {{ row.productName }}
            </span>
            <span class="text-muted">({{ row.productKey }})</span>
          </template>
        </el-table-column>
        <el-table-column label="分组" min-width="120">
          <template #default="{ row }">
            <span v-if="row.groupName">{{ row.groupName }}</span>
            <span v-else class="text-muted">未分组</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.status]?.type as any" size="small">
              {{ statusMap[row.status]?.label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="健康度" width="110">
          <template #default="{ row }">
            <template v-if="row.healthScore != null">
              <el-progress :percentage="row.healthScore" :stroke-width="6" :show-text="false"
                :color="row.healthScore >= 75 ? '#67c23a' : row.healthScore >= 60 ? '#e6a23c' : '#f56c6c'" />
              <span class="text-secondary text-xs ml-4">{{ row.healthScore }}</span>
            </template>
            <span v-else class="text-disabled">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="location" label="位置" min-width="140" show-overflow-tooltip />
        <el-table-column label="密钥" width="160">
          <template #default="{ row }">
            <code class="secret">{{ row.deviceSecret }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="lastOnlineTime" label="最近上线" width="170" />
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-button link type="info" :icon="Connection" @click="openShadow(row)">影子</el-button>
            <el-button link type="warning" :icon="Key" @click="onResetSecret(row)">密钥</el-button>
            <el-button link :type="row.status === 2 ? 'success' : 'danger'"
              @click="onToggleStatus(row)">
              {{ row.status === 2 ? '启用' : '禁用' }}
            </el-button>
            <el-button link type="danger" :icon="Delete" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无设备" /></template>
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
      :title="dialogMode === 'create' ? '新建设备' : '编辑设备'"
      width="600px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" @submit.prevent>
        <el-form-item label="产品" prop="productId">
          <el-select v-model="form.productId" :disabled="dialogMode === 'edit'" style="width: 100%">
            <el-option v-for="p in productOptions" :key="p.id"
              :label="`${p.productKey} - ${p.productName}`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="设备 Key" prop="deviceKey">
          <el-input v-model="form.deviceKey" placeholder="如 TH-001 / SN-ABC123" :disabled="dialogMode === 'edit'" />
        </el-form-item>
        <el-form-item label="设备名" prop="deviceName">
          <el-input v-model="form.deviceName" placeholder="中文名,如 车间一-001" />
        </el-form-item>
        <el-form-item label="分组">
          <el-select v-model="form.groupId" clearable style="width: 100%">
            <el-option v-for="g in groupOptions" :key="g.id" :label="g.groupName" :value="g.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="设备密钥">
          <el-input v-model="form.deviceSecret" placeholder="留空自动生成 / 自定义" />
          <div class="hint">仅创建时可设置;之后请用"密钥"按钮重置</div>
        </el-form-item>
        <el-form-item label="位置">
          <el-input v-model="form.location" placeholder="如 一号车间-A 区" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">
          {{ dialogMode === 'create' ? '创建' : '保存' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.search-bar { margin-bottom: 12px; padding: 16px; :deep(.el-form-item) { margin-bottom: 0; } }
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 16px; }
.hint { color: #909399; font-size: 12px; margin-top: 4px; }
.text-muted { color: #c0c4cc; font-size: 12px; margin-left: 4px; }
.link-like { color: #409eff; cursor: pointer; }
.link-like:hover { text-decoration: underline; }
.secret {
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 3px;
  font-family: 'Menlo', 'Consolas', monospace;
  font-size: 12px;
}
.ml-4 { margin-left: 4px; }
</style>