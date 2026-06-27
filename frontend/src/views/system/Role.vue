<script setup lang="ts">
import { ref, reactive, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Setting, Key, Search, Refresh } from '@element-plus/icons-vue'
import {
  pageRoles,
  createRole,
  updateRole,
  deleteRole,
  getRoleMenuIds,
  assignRoleMenus,
  type SysRoleVO,
  type RoleDTO
} from '@/api/system/role'
import { getMenuTree, type SysMenuTreeVO } from '@/api/system/menu'

// ========== 列表 ==========
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '' })
const loading = ref(false)
const list = ref<SysRoleVO[]>([])
const total = ref(0)

async function load() {
  loading.value = true
  try {
    const res: any = await pageRoles(query)
    list.value = res.data.records ?? []
    total.value = res.data.total ?? 0
  } finally {
    loading.value = false
  }
}
function onSearch() { query.pageNum = 1; load() }
function onReset() { query.keyword = ''; query.pageNum = 1; load() }
function onPageChange(p: number) { query.pageNum = p; load() }
function onSizeChange(s: number) { query.pageSize = s; query.pageNum = 1; load() }

// ========== 新建/编辑对话框 ==========
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const submitting = ref(false)
const formRef = ref()
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
  if (!formRef.value) return
  let valid = false
  try { valid = await formRef.value.validate() } catch { valid = false }
  if (!valid) return

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
    load()
  } catch (e) {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}

async function onDelete(row: SysRoleVO) {
  await ElMessageBox.confirm(`确认删除角色「${row.roleName}」?`, '删除确认', {
    type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
  })
  try {
    await deleteRole(row.id)
    ElMessage.success('删除成功')
    load()
  } catch {}
}

// ========== 分配权限 ==========
const permDialog = ref(false)
const permSubmitting = ref(false)
const permTreeRef = ref()
const permTarget = ref<SysRoleVO | null>(null)
const menuTree = ref<SysMenuTreeVO[]>([])
const treeProps = { children: 'children', label: 'menuName' }
const checkedKeys = ref<number[]>([])
// 树节点只在叶子菜单(menuType=2)上显示 checkbox,父节点点击展开/收起
const treeNodeProps = (data: SysMenuTreeVO) => ({
  disabled: data.menuType === 1,   // 目录不参与勾选(只勾叶子菜单/按钮)
})

async function openAssignPerm(row: SysRoleVO) {
  permTarget.value = row
  // 取菜单树 + 当前已分配的菜单
  const [treeRes, idsRes]: any[] = await Promise.all([
    getMenuTree(),
    getRoleMenuIds(row.id)
  ])
  menuTree.value = treeRes.data ?? []
  checkedKeys.value = idsRes.data ?? []
  permDialog.value = true
  // 等树渲染后再设置勾选
  nextTick(() => {
    // el-tree 的 setCheckedKeys 在 half-checked 时不会自动勾父节点,
    // 所以我们手动递归勾上所有父节点
    expandAndCheckParents(menuTree.value, new Set(checkedKeys.value))
  })
}

// 把所有"已勾选节点的祖先"也加入 checkedKeys,保证树渲染时父节点显示勾
function expandAndCheckParents(nodes: SysMenuTreeVO[], selected: Set<number>) {
  function findParents(list: SysMenuTreeVO[], target: number, parents: number[]): boolean {
    for (const n of list) {
      if (n.id === target) return true
      if (n.children?.length) {
        const found = findParents(n.children, target, [...parents, n.id])
        if (found) {
          selected.add(n.id)
          return true
        }
      }
    }
    return false
  }
  for (const id of [...selected]) findParents(nodes, id, [])
  checkedKeys.value = [...selected]
}

async function onAssignPermSubmit() {
  if (!permTarget.value) return
  // 只收集叶子节点(menuType=2)的 id,父节点由后端根据业务需要决定
  const checked = permTreeRef.value.getCheckedNodes(false, true) as SysMenuTreeVO[]
  const menuIds = checked.filter(n => n.menuType !== 1).map(n => n.id)
  if (menuIds.length === 0) {
    await ElMessageBox.confirm('该角色未勾选任何菜单权限,将无法访问任何菜单,确认提交?', '提示', { type: 'warning' })
  }
  permSubmitting.value = true
  try {
    await assignRoleMenus(permTarget.value.id, menuIds)
    ElMessage.success(`已为「${permTarget.value.roleName}」分配 ${menuIds.length} 个权限`)
    permDialog.value = false
  } catch (e) {
    // 拦截器已提示
  } finally {
    permSubmitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">角色管理</h2>

    <div class="page-card search-bar">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="角色编码 / 角色名" clearable style="width: 220px"
            @keyup.enter="onSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
          <el-button :icon="Refresh" @click="onReset">重置</el-button>
          <el-button type="success" :icon="Plus" @click="openCreate">新建角色</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="page-card">
      <el-table v-loading="loading" :data="list" stripe border>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="roleCode" label="角色编码" min-width="160" />
        <el-table-column prop="roleName" label="角色名" min-width="160" />
        <el-table-column prop="description" label="描述" min-width="180" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.builtIn === 1" type="warning" size="small">内置</el-tag>
            <el-tag v-else type="info" size="small">自定义</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-button link type="success" :icon="Key" @click="openAssignPerm(row)">分配权限</el-button>
            <el-button link type="danger" :icon="Delete" :disabled="row.builtIn === 1"
              @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无角色" /></template>
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
      :title="dialogMode === 'create' ? '新建角色' : '编辑角色'"
      width="480px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" @submit.prevent>
        <el-form-item label="角色编码" prop="roleCode">
          <el-input v-model="form.roleCode" placeholder="例如 VIEWER / OPERATOR" />
        </el-form-item>
        <el-form-item label="角色名" prop="roleName">
          <el-input v-model="form.roleName" placeholder="展示用,如 只读用户" />
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

    <!-- 分配权限对话框 -->
    <el-dialog v-model="permDialog" width="520px" destroy-on-close
      :title="`分配权限 - ${permTarget?.roleName ?? ''}`">
      <el-alert type="info" :closable="false" style="margin-bottom: 12px">
        目录(无法勾选)仅作分组;只有叶子菜单(实际页面)被勾选后,该角色才能访问对应页面。
      </el-alert>
      <el-tree
        ref="permTreeRef"
        :data="menuTree"
        :props="treeProps"
        node-key="id"
        show-checkbox
        default-expand-all
        :check-strictly="false"
        :node-props="treeNodeProps"
        v-model:checked-keys="checkedKeys" />
      <template #footer>
        <el-button @click="permDialog = false">取消</el-button>
        <el-button type="primary" :loading="permSubmitting" @click="onAssignPermSubmit">保存权限</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.search-bar {
  margin-bottom: $spacing-12;
  padding: $spacing-16;
  :deep(.el-form-item) { margin-bottom: 0; }
}
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: $spacing-16;
}
</style>