<script setup lang="ts">
/**
 * 角色管理 — <CrudList> 重构版 (425 → ~270 行)
 *
 * 设计要点:
 *   - 列表/筛选/分页 由 <CrudList> 接管
 *   - 新建/编辑对话框 用 <ModalForm> 统一
 *   - "分配权限"对话框保留(el-tree 自定义交互逻辑多)
 *   - 内置角色 builtIn=1 禁用删除
 */
import { ref, reactive, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Key } from '@element-plus/icons-vue'
import {
  roleCrud,
  createRole,
  updateRole,
  getRoleMenuIds,
  assignRoleMenus,
  type SysRoleVO,
  type RoleDTO
} from '@/api/system/role'
import { getMenuTree, type SysMenuTreeVO } from '@/api/system/menu'
import { CrudList, ModalForm, type ColumnDef, type FilterItem, type StatusType } from '@/ui'

// ========== 列表 ==========
const filters: FilterItem[] = []  // 角色列表只有关键字搜索,无分类筛选

const columns: ColumnDef<SysRoleVO>[] = [
  { prop: 'id', label: 'ID', width: 80 },
  { prop: 'roleCode', label: '角色编码', minWidth: 160 },
  { prop: 'roleName', label: '角色名', minWidth: 160 },
  { prop: 'description', label: '描述', minWidth: 180, showOverflowTooltip: true },
  { prop: 'builtIn', label: '类型', width: 100, slot: 'type' },
  { prop: 'createdAt', label: '创建时间', width: 170 },
  { label: '操作', width: 260, fixed: 'right', slot: 'actions' }
]

const TYPE_TYPE_MAP: Record<string, StatusType> = { '0': 'info', '1': 'warning' }
const TYPE_LABEL_MAP: Record<number, string> = { 0: '自定义', 1: '内置' }

const crudListRef = ref<{ refresh: () => Promise<void> } | null>(null)
function refresh(): void { void crudListRef.value?.refresh() }

// ========== 新建/编辑对话框 ==========
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const submitting = ref(false)
const form = reactive<RoleDTO>({ id: undefined, roleCode: '', roleName: '', description: '' })
const rules = {
  roleCode: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
  roleName: [{ required: true, message: '请输入角色名', trigger: 'blur' }]
}

function openCreate() {
  dialogMode.value = 'create'
  Object.assign(form, { id: undefined, roleCode: '', roleName: '', description: '' })
  dialogVisible.value = true
}

function openEdit(row: SysRoleVO) {
  dialogMode.value = 'edit'
  Object.assign(form, { id: row.id, roleCode: row.roleCode, roleName: row.roleName, description: row.description ?? '' })
  dialogVisible.value = true
}

async function onSubmit() {
  submitting.value = true
  try {
    if (dialogMode.value === 'create') {
      await createRole(form)
      ElMessage.success('创建成功')
    } else {
      await updateRole(form)
      ElMessage.success('更新成功')
    }
    dialogVisible.value = false
    refresh()
  } catch { /* 拦截器已提示 */ } finally { submitting.value = false }
}

async function onDelete(row: SysRoleVO) {
  await ElMessageBox.confirm(`确认删除角色「${row.roleName}」?`, '删除确认', {
    type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
  })
  try {
    await roleCrud.remove!(row.id)
    ElMessage.success('删除成功')
    refresh()
  } catch { /* ignore */ }
}

// ========== 分配权限(el-tree 自定义交互,保留内联) ==========
const permDialog = ref(false)
const permSubmitting = ref(false)
const permTreeRef = ref()
const permTarget = ref<SysRoleVO | null>(null)
const menuTree = ref<SysMenuTreeVO[]>([])
const treeProps = { children: 'children', label: 'menuName' }
const checkedKeys = ref<number[]>([])
// 树节点只在叶子菜单(menuType=2)上可勾选,父节点点击展开/收起
const treeNodeProps = (data: SysMenuTreeVO) => ({
  disabled: data.menuType === 1   // 目录不参与勾选(只勾叶子菜单/按钮)
})

async function openAssignPerm(row: SysRoleVO) {
  permTarget.value = row
  const [treeRes, idsRes]: any[] = await Promise.all([
    getMenuTree(),
    getRoleMenuIds(row.id)
  ])
  menuTree.value = treeRes.data ?? []
  checkedKeys.value = idsRes.data ?? []
  permDialog.value = true
  // 等树渲染后再补勾父节点(用于显示半选 → 全选的视觉)
  nextTick(() => {
    expandAndCheckParents(menuTree.value, new Set(checkedKeys.value))
  })
}

function expandAndCheckParents(nodes: SysMenuTreeVO[], selected: Set<number>) {
  function findParents(list: SysMenuTreeVO[], target: number, parents: number[]): boolean {
    for (const n of list) {
      if (n.id === target) return true
      if (n.children?.length) {
        const found = findParents(n.children, target, [...parents, n.id])
        if (found) { selected.add(n.id); return true }
      }
    }
    return false
  }
  for (const id of [...selected]) findParents(nodes, id, [])
  checkedKeys.value = [...selected]
}

async function onAssignPermSubmit() {
  if (!permTarget.value) return
  // 只收集叶子节点(menuType=2)的 id
  const checked = permTreeRef.value.getCheckedNodes(false, true) as SysMenuTreeVO[]
  const menuIds = checked.filter(n => n.menuType !== 1).map(n => n.id)
  if (menuIds.length === 0) {
    await ElMessageBox.confirm(
      '该角色未勾选任何菜单权限,将无法访问任何菜单,确认提交?',
      '提示', { type: 'warning' }
    )
  }
  permSubmitting.value = true
  try {
    await assignRoleMenus(permTarget.value.id, menuIds)
    ElMessage.success(`已为「${permTarget.value.roleName}」分配 ${menuIds.length} 个权限`)
    permDialog.value = false
  } catch { /* 拦截器已提示 */ } finally { permSubmitting.value = false }
}

// 触发一次初始加载(为热路径)
onMounted(() => { void refresh() })
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">
      角色管理
    </h2>

    <CrudList
      ref="crudListRef"
      :api="roleCrud"
      :columns="columns"
      :filters="filters"
      :row-key="'id'"
      empty-text="暂无角色"
      keyword-placeholder="角色编码 / 角色名"
    >
      <template #toolbar>
        <el-button
          type="success"
          :icon="Plus"
          @click="openCreate"
        >
          新建角色
        </el-button>
      </template>

      <template #column-type="{ row }">
        <StatusTag
          :value="(row as SysRoleVO).builtIn ?? 0"
          :label="TYPE_LABEL_MAP[(row as SysRoleVO).builtIn ?? 0]"
          :type-map="TYPE_TYPE_MAP"
        />
      </template>

      <template #column-actions="{ row }">
        <el-button
          link
          type="primary"
          :icon="Edit"
          @click="openEdit(row as SysRoleVO)"
        >
          编辑
        </el-button>
        <el-button
          link
          type="success"
          :icon="Key"
          @click="openAssignPerm(row as SysRoleVO)"
        >
          分配权限
        </el-button>
        <el-button
          link
          type="danger"
          :icon="Delete"
          :disabled="(row as SysRoleVO).builtIn === 1"
          @click="onDelete(row as SysRoleVO)"
        >
          删除
        </el-button>
      </template>
    </CrudList>

    <!-- 新建/编辑对话框:用 ModalForm 统一 -->
    <ModalForm
      v-model:visible="dialogVisible"
      :title="dialogMode === 'create' ? '新建角色' : '编辑角色'"
      :width="480"
      :model="form"
      :rules="rules"
      :loading="submitting"
      submit-text="保存"
      @submit="onSubmit"
    >
      <el-form-item label="角色编码" prop="roleCode">
        <el-input
          v-model="form.roleCode"
          placeholder="例如 VIEWER / OPERATOR"
        />
      </el-form-item>
      <el-form-item label="角色名" prop="roleName">
        <el-input
          v-model="form.roleName"
          placeholder="展示用,如 只读用户"
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

    <!-- 分配权限:el-tree 自定义交互,保留内联 -->
    <el-dialog
      v-model="permDialog"
      width="520px"
      destroy-on-close
      :title="`分配权限 - ${permTarget?.roleName ?? ''}`"
    >
      <el-alert
        type="info"
        :closable="false"
        style="margin-bottom: 12px"
      >
        目录(无法勾选)仅作分组;只有叶子菜单(实际页面)被勾选后,该角色才能访问对应页面。
      </el-alert>
      <el-tree
        ref="permTreeRef"
        v-model:checked-keys="checkedKeys"
        :data="menuTree"
        :props="treeProps"
        node-key="id"
        show-checkbox
        default-expand-all
        :check-strictly="false"
        :node-props="treeNodeProps"
      />
      <template #footer>
        <el-button @click="permDialog = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="permSubmitting"
          @click="onAssignPermSubmit"
        >
          保存权限
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;
</style>