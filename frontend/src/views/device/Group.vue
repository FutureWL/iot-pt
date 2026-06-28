<script setup lang="ts">
/**
 * 设备分组 — <CrudList> + <ModalForm> 重构版 (243 → ~165 行)
 *
 * 设计要点:
 *   - 列表/筛选/分页 由 <CrudList> 接管
 *   - 后端新增 /iot/device-group/page 端点(原本只有 /all)
 *   - 新建/编辑对话框 用 <ModalForm> 统一
 */
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Folder } from '@element-plus/icons-vue'
import {
  groupCrud,
  createGroup,
  updateGroup,
  type IotDeviceGroupVO,
  type IotDeviceGroupDTO
} from '@/api/iot/deviceGroup'
import { CrudList, type ColumnDef } from '@/ui'

// ========== 列表 ==========
const columns: ColumnDef<IotDeviceGroupVO>[] = [
  { prop: 'id', label: 'ID', width: 80 },
  { prop: 'groupName', label: '分组名', minWidth: 200, slot: 'groupName' },
  { prop: 'description', label: '描述', minWidth: 240, showOverflowTooltip: true },
  { prop: 'sort', label: '排序', width: 80 },
  { prop: 'deviceCount', label: '设备数', width: 100, slot: 'deviceCount' },
  { prop: 'createdAt', label: '创建时间', width: 170 },
  { label: '操作', width: 180, fixed: 'right', slot: 'actions' }
]

const crudListRef = ref<{ refresh: () => Promise<void> } | null>(null)
function refresh(): void { void crudListRef.value?.refresh() }

// ========== 新建/编辑对话框 ==========
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const submitting = ref(false)
const form = reactive<IotDeviceGroupDTO>({
  id: undefined, parentId: 0, groupName: '', description: '', sort: 0
})
const rules = {
  groupName: [{ required: true, message: '请输入分组名', trigger: 'blur' }]
}

function openCreate() {
  dialogMode.value = 'create'
  Object.assign(form, { id: undefined, parentId: 0, groupName: '', description: '', sort: 0 })
  dialogVisible.value = true
}

function openEdit(row: IotDeviceGroupVO) {
  dialogMode.value = 'edit'
  Object.assign(form, {
    id: row.id, parentId: row.parentId, groupName: row.groupName,
    description: row.description ?? '', sort: row.sort
  })
  dialogVisible.value = true
}

async function onSubmit() {
  submitting.value = true
  try {
    if (dialogMode.value === 'create') {
      await createGroup(form)
      ElMessage.success('创建成功')
    } else {
      await updateGroup(form)
      ElMessage.success('更新成功')
    }
    dialogVisible.value = false
    refresh()
  } catch { /* 拦截器已提示 */ } finally { submitting.value = false }
}

async function onDelete(row: IotDeviceGroupVO) {
  await ElMessageBox.confirm(`确认删除分组「${row.groupName}」?`, '删除确认', { type: 'warning' })
  try {
    await groupCrud.remove!(row.id)
    ElMessage.success('删除成功')
    refresh()
  } catch { /* ignore */ }
}
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">
      设备分组
    </h2>

    <div class="page-card hint-bar">
      <span class="hint">
        分组用于把设备归类,如按车间、按项目、按客户。新建设备时可选择分组。
      </span>
    </div>

    <CrudList
      ref="crudListRef"
      :api="groupCrud"
      :columns="columns"
      :filters="[]"
      :row-key="'id'"
      empty-text="暂无分组"
      keyword-placeholder="分组名 / 描述"
    >
      <template #toolbar>
        <el-button
          type="success"
          :icon="Plus"
          @click="openCreate"
        >
          新建分组
        </el-button>
      </template>

      <template #column-groupName="{ row }">
        <el-icon><Folder /></el-icon>
        <span style="margin-left: 6px; font-weight: 500">{{ (row as IotDeviceGroupVO).groupName }}</span>
      </template>

      <template #column-deviceCount="{ row }">
        <el-tag
          :type="(row as IotDeviceGroupVO).deviceCount > 0 ? 'success' : 'info'"
          size="small"
        >
          {{ (row as IotDeviceGroupVO).deviceCount }} 台
        </el-tag>
      </template>

      <template #column-actions="{ row }">
        <el-button
          link
          type="primary"
          :icon="Edit"
          @click="openEdit(row as IotDeviceGroupVO)"
        >
          编辑
        </el-button>
        <el-button
          link
          type="danger"
          :icon="Delete"
          @click="onDelete(row as IotDeviceGroupVO)"
        >
          删除
        </el-button>
      </template>
    </CrudList>

    <!-- 新建/编辑对话框:用 ModalForm 统一 -->
    <ModalForm
      v-model:visible="dialogVisible"
      :title="dialogMode === 'create' ? '新建分组' : '编辑分组'"
      :width="480"
      :model="form"
      :rules="rules"
      :loading="submitting"
      :submit-text="dialogMode === 'create' ? '创建' : '保存'"
      @submit="onSubmit"
    >
      <el-form-item label="分组名" prop="groupName">
        <el-input v-model="form.groupName" placeholder="如 一号车间" />
      </el-form-item>
      <el-form-item label="排序">
        <el-input-number
          v-model="form.sort"
          :min="0"
          :max="9999"
        />
      </el-form-item>
      <el-form-item label="描述">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="2"
        />
      </el-form-item>
    </ModalForm>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.hint-bar {
  margin-bottom: $spacing-12;
  padding: $spacing-8 $spacing-16;
  .hint {
    color: var(--iot-text-secondary);
    font-size: $font-size-small;
  }
}
</style>