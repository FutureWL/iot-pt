<script setup lang="ts">
/**
 * 组织架构 — <ModalForm> 改造版 (240 → ~190 行)
 *
 * 设计要点:
 *   - 列表用 el-tree(树形结构,不适合 CrudList)
 *   - 新建/编辑对话框 用 <ModalForm> 统一
 *   - 支持新建顶级 + 在任一节点下新建子组织
 */
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Share } from '@element-plus/icons-vue'
import {
  treeOrganizations,
  createOrganization,
  updateOrganization,
  deleteOrganization,
  type SysOrganizationVO
} from '@/api/system/organization'
import { ModalForm } from '@/ui'

const loading = ref(false)
const tree = ref<SysOrganizationVO[]>([])

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit' | 'create-child'>('create')
const submitting = ref(false)
const form = ref<Partial<SysOrganizationVO>>({})

const rules = {
  name: [{ required: true, message: '请输入组织名称', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    const res: any = await treeOrganizations()
    tree.value = res.data ?? []
  } finally { loading.value = false }
}

function openCreate(parent?: SysOrganizationVO) {
  dialogMode.value = parent ? 'create-child' : 'create'
  form.value = {
    parentId: parent?.id ?? 0,
    name: '', sort: 0, leader: '', phone: '', description: ''
  }
  dialogVisible.value = true
}

function openEdit(node: SysOrganizationVO) {
  dialogMode.value = 'edit'
  form.value = { ...node }
  dialogVisible.value = true
}

async function onSubmit() {
  submitting.value = true
  try {
    if (dialogMode.value === 'edit') {
      await updateOrganization(form.value)
      ElMessage.success('已更新')
    } else {
      await createOrganization(form.value)
      ElMessage.success('已创建')
    }
    dialogVisible.value = false
    await load()
  } catch { /* 拦截器已提示 */ } finally { submitting.value = false }
}

async function onDelete(node: SysOrganizationVO) {
  await ElMessageBox.confirm(
    `确认删除组织「${node.name}」?若存在子组织将一并删除。`,
    '删除确认', { type: 'warning' }
  )
  try {
    await deleteOrganization(node.id)
    ElMessage.success('已删除')
    await load()
  } catch { /* ignore */ }
}

onMounted(load)
</script>

<template>
  <div
    v-loading="loading"
    class="page-container org-page"
  >
    <div class="page-header">
      <h2 class="page-title">
        组织架构
      </h2>
      <el-button
        type="primary"
        :icon="Plus"
        @click="openCreate()"
      >
        新建顶级组织
      </el-button>
    </div>

    <div class="page-card">
      <div class="info-bar">
        <el-icon><Share /></el-icon>
        <span>支持树形组织管理(省/市/公司/部门/班组),用于工单归属、人员分配、统计分组。</span>
      </div>
      <el-tree
        v-if="tree.length > 0"
        :data="tree"
        node-key="id"
        :props="{ label: 'name', children: 'children' }"
        default-expand-all
        class="org-tree"
      >
        <template #default="{ node, data }">
          <div class="tree-node">
            <span class="tree-name">{{ node.label }}</span>
            <span
              v-if="data.leader"
              class="tree-leader text-secondary text-xs"
            >负责人: {{ data.leader }}</span>
            <span class="tree-actions">
              <el-button
                link
                type="primary"
                size="small"
                :icon="Plus"
                @click.stop="openCreate(data)"
              >下级</el-button>
              <el-button
                link
                type="primary"
                size="small"
                :icon="Edit"
                @click.stop="openEdit(data)"
              >编辑</el-button>
              <el-button
                link
                type="danger"
                size="small"
                :icon="Delete"
                @click.stop="onDelete(data)"
              >删除</el-button>
            </span>
          </div>
        </template>
      </el-tree>
      <el-empty
        v-else
        description="暂无组织数据,请新建"
      />
    </div>

    <!-- 新建/编辑对话框:用 ModalForm 统一 -->
    <ModalForm
      v-model:visible="dialogVisible"
      :title="dialogMode === 'edit' ? '编辑组织' : '新建组织'"
      :width="540"
      :model="form"
      :rules="rules"
      :loading="submitting"
      submit-text="保存"
      @submit="onSubmit"
    >
      <el-form-item label="父组织">
        <el-input
          :model-value="form.parentId === 0 ? '(顶级)' : form.parentId"
          disabled
        />
      </el-form-item>
      <el-form-item label="名称" prop="name">
        <el-input v-model="form.name" placeholder="组织名称" />
      </el-form-item>
      <el-form-item label="排序">
        <el-input-number
          v-model="form.sort"
          :min="0"
          :max="9999"
          controls-position="right"
        />
      </el-form-item>
      <el-form-item label="负责人">
        <el-input v-model="form.leader" placeholder="负责人姓名" />
      </el-form-item>
      <el-form-item label="联系电话">
        <el-input v-model="form.phone" placeholder="如 138-0000-0000" />
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

.org-page { background: var(--iot-bg-page); }
.page-header { display: flex; align-items: center; gap: $spacing-12; margin-bottom: $spacing-16; .page-title { margin: 0; flex: 1; } }

.info-bar {
  display: flex; align-items: center; gap: $spacing-8;
  padding: $spacing-8 $spacing-12; background: var(--iot-color-primary-light-9);
  border-radius: $radius-base; color: var(--iot-color-primary);
  font-size: $font-size-small; margin-bottom: $spacing-16;
}

.org-tree { padding: $spacing-8; }
.tree-node { flex: 1; display: flex; align-items: center; gap: $spacing-12; padding-right: $spacing-8; }
.tree-name { font-weight: $font-weight-medium; color: var(--iot-text-primary); }
.tree-leader { margin-left: $spacing-8; }
.tree-actions { margin-left: auto; }
</style>