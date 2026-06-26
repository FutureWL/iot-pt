<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Folder, Refresh } from '@element-plus/icons-vue'
import {
  allGroups,
  createGroup,
  updateGroup,
  deleteGroup,
  type IotDeviceGroupVO,
  type IotDeviceGroupDTO
} from '@/api/iot/deviceGroup'

const list = ref<IotDeviceGroupVO[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const formRef = ref()
const form = reactive<IotDeviceGroupDTO>({
  id: undefined, parentId: 0, groupName: '', description: '', sort: 0
})
const rules = {
  groupName: [{ required: true, message: '请输入分组名', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    const res: any = await allGroups()
    list.value = res.data ?? []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  dialogMode.value = 'create'
  Object.assign(form, { id: undefined, parentId: 0, groupName: '', description: '', sort: 0 })
  dialogVisible.value = true
}

function openEdit(row: IotDeviceGroupVO) {
  dialogMode.value = 'edit'
  Object.assign(form, { id: row.id, parentId: row.parentId, groupName: row.groupName,
    description: row.description ?? '', sort: row.sort })
  dialogVisible.value = true
}

async function onSubmit() {
  if (!formRef.value) return
  let valid = false
  try { valid = await formRef.value.validate() } catch { valid = false }
  if (!valid) return
  try {
    if (dialogMode.value === 'create') {
      await createGroup(form)
      ElMessage.success('创建成功')
    } else {
      await updateGroup(form)
      ElMessage.success('更新成功')
    }
    dialogVisible.value = false
    load()
  } catch (e) {}
}

async function onDelete(row: IotDeviceGroupVO) {
  await ElMessageBox.confirm(`确认删除分组「${row.groupName}」?`, '删除确认', { type: 'warning' })
  try {
    await deleteGroup(row.id)
    ElMessage.success('删除成功')
    load()
  } catch {}
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">设备分组</h2>

    <div class="page-card search-bar">
      <span class="hint">分组用于把设备归类,如按车间、按项目、按客户。新建设备时可选择分组。</span>
      <el-button type="success" :icon="Plus" @click="openCreate">新建分组</el-button>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <div class="page-card">
      <el-table v-loading="loading" :data="list" stripe border>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="分组名" min-width="200">
          <template #default="{ row }">
            <el-icon><Folder /></el-icon>
            <span style="margin-left: 6px; font-weight: 500">{{ row.groupName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="240" show-overflow-tooltip />
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column label="设备数" width="100">
          <template #default="{ row }">
            <el-tag :type="row.deviceCount > 0 ? 'success' : 'info'" size="small">
              {{ row.deviceCount }} 台
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" :icon="Delete" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无分组" /></template>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新建分组' : '编辑分组'"
      width="480px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px" @submit.prevent>
        <el-form-item label="分组名" prop="groupName">
          <el-input v-model="form.groupName" placeholder="如 一号车间" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSubmit">
          {{ dialogMode === 'create' ? '创建' : '保存' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.search-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  padding: 16px;
  .hint { color: #909399; font-size: 13px; flex: 1; }
}
</style>