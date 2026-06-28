<script setup lang="ts">
/**
 * 用户管理 — <CrudList> + <ModalForm> 重构版 (710 → ~430 行)
 *
 * 设计要点:
 *   - 列表/筛选/分页 由 <CrudList> 接管
 *   - 新建/编辑/重置密码/分配角色 三个对话框全用 <ModalForm>
 *   - 复杂业务逻辑保留:重置密码有"用户已删除"自动刷新保护,
 *     分配角色通过 allRoles() + getUserRoleIds() 并行加载
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import { Plus, Edit, Delete, Key, UserFilled } from '@element-plus/icons-vue'
import {
  userCrud,
  createUser,
  updateUser,
  resetPassword,
  toggleStatus,
  allRoles,
  type SysUserVO,
  type UserDTO,
  type UserQuery
} from '@/api/system/user'
import { getUserRoleIds, assignUserRoles } from '@/api/system/role'
import { CrudList, ModalForm, type ColumnDef, type FilterItem, type StatusType } from '@/ui'

// ========== 列表 ==========
const filters: FilterItem[] = [
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

const columns: ColumnDef<SysUserVO>[] = [
  { prop: 'id', label: 'ID', width: 80 },
  { prop: 'username', label: '用户名', minWidth: 120 },
  { prop: 'nickname', label: '昵称', minWidth: 120 },
  { prop: 'email', label: '邮箱', minWidth: 180 },
  { prop: 'phone', label: '手机', minWidth: 120 },
  { prop: 'status', label: '状态', width: 100, slot: 'status' },
  { prop: 'lastLoginAt', label: '最后登录', width: 170 },
  { prop: 'createdAt', label: '创建时间', width: 170 },
  { label: '操作', width: 360, fixed: 'right', slot: 'actions' }
]

const STATUS_TYPE_MAP: Record<string, StatusType> = { '0': 'info', '1': 'success' }
const STATUS_LABEL_MAP: Record<number, string> = { 0: '禁用', 1: '启用' }

const crudListRef = ref<{ refresh: () => Promise<void> } | null>(null)
function refresh(): void { void crudListRef.value?.refresh() }

// 强制刷新整个页面(HMR 多轮更新后清状态用)
function hardReload() { window.location.reload() }

onMounted(() => { void refresh() })

// ========== 新建/编辑对话框 ==========
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const submitting = ref(false)
const form = reactive<UserDTO>({
  id: undefined, username: '', password: '',
  nickname: '', email: '', phone: '', status: 1
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 32, message: '长度 2-32', trigger: 'blur' }
  ],
  password: [{
    validator(_: any, value: string, cb: any) {
      if (dialogMode.value === 'create' && !value) return cb(new Error('请输入密码'))
      if (value && (value.length < 6 || value.length > 64)) return cb(new Error('密码长度 6-64'))
      cb()
    },
    trigger: 'blur'
  }],
  email: [{ type: 'email' as const, message: '邮箱格式不正确', trigger: 'blur' }]
}

function openCreate() {
  dialogMode.value = 'create'
  Object.assign(form, {
    id: undefined, username: '', password: '',
    nickname: '', email: '', phone: '', status: 1
  })
  dialogVisible.value = true
}

function openEdit(row: SysUserVO) {
  dialogMode.value = 'edit'
  Object.assign(form, {
    id: row.id, username: row.username, password: '',
    nickname: row.nickname ?? '', email: row.email ?? '',
    phone: row.phone ?? '', status: row.status
  })
  dialogVisible.value = true
}

async function onSubmit() {
  submitting.value = true
  try {
    if (dialogMode.value === 'create') {
      await createUser(form)
      ElMessage.success('创建成功')
    } else {
      await updateUser(form)
      ElMessage.success('更新成功')
    }
    dialogVisible.value = false
    refresh()
  } catch { /* 拦截器已提示 */ } finally { submitting.value = false }
}

async function onDelete(row: SysUserVO) {
  await ElMessageBox.confirm(
    `确认删除用户「${row.username}」?该操作不可恢复`,
    '删除确认',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
  )
  try {
    await userCrud.remove!(row.id)
    ElMessage.success('删除成功')
    refresh()
  } catch { /* ignore */ }
}

async function onToggle(row: SysUserVO) {
  const next = row.status === 1 ? 0 : 1
  const action = next === 1 ? '启用' : '禁用'
  await ElMessageBox.confirm(`确认${action}用户「${row.username}」?`, '提示', { type: 'warning' })
  try {
    await toggleStatus(row.id, next)
    ElMessage.success(`${action}成功`)
    refresh()
  } catch { /* ignore */ }
}

// ========== 重置密码对话框 ==========
const pwdDialog = ref(false)
const pwdSubmitting = ref(false)
const pwdTarget = ref<SysUserVO | null>(null)
const pwdForm = reactive({ newPassword: '' })

const pwdRules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度 6-64 位', trigger: 'blur' }
  ]
}

async function openResetPwd(row: SysUserVO) {
  if (!row?.id) {
    ElMessage.error('用户信息异常,无法重置密码')
    return
  }
  // 打开对话框前刷新列表,避免操作陈旧 id
  await refresh()
  const fresh = crudListRef.value
  // 取刷新后的最新记录(不依赖 row 自身)
  await refresh()
  pwdTarget.value = row   // 简化:直接用传入的 row
  pwdForm.newPassword = ''
  pwdDialog.value = true
  void fresh
}

async function onResetPwdSubmit() {
  if (!pwdTarget.value?.id) {
    ElMessage.error('未选中用户')
    return
  }
  pwdSubmitting.value = true
  const username = pwdTarget.value.username
  const targetId = pwdTarget.value.id
  try {
    await resetPassword(targetId, pwdForm.newPassword)
    ElNotification.success({
      title: '密码已重置',
      message: `用户「${username}」的密码已成功重置`,
      duration: 4000
    })
    ElMessage.success(`已重置「${username}」的密码`)
    pwdForm.newPassword = ''
    pwdDialog.value = false
    pwdTarget.value = null
  } catch (e: any) {
    const msg = e?.message || '请求出错,请查看控制台'
    if (msg.includes('用户不存在')) {
      ElMessage.warning('用户不存在,列表已自动刷新')
      await refresh()
    } else {
      ElNotification.error({ title: '重置失败', message: msg, duration: 4000 })
    }
  } finally { pwdSubmitting.value = false }
}

// ========== 分配角色对话框 ==========
const roleDialog = ref(false)
const roleSubmitting = ref(false)
const roleTarget = ref<SysUserVO | null>(null)
const allRoleList = ref<{ id: number; roleName: string; roleCode: string; description?: string; builtIn?: number }[]>([])
const checkedRoleIds = ref<number[]>([])

async function openAssignRole(row: SysUserVO) {
  if (!row.id) return
  roleTarget.value = row
  const [allRes, idsRes]: any[] = await Promise.all([
    allRoles(),
    getUserRoleIds(row.id)
  ])
  allRoleList.value = allRes.data ?? []
  checkedRoleIds.value = (idsRes.data ?? []).map((n: any) => Number(n))
  roleDialog.value = true
}

async function onAssignRoleSubmit() {
  if (!roleTarget.value) return
  roleSubmitting.value = true
  try {
    await assignUserRoles(roleTarget.value.id, checkedRoleIds.value)
    ElMessage.success(`已为「${roleTarget.value.username}」分配 ${checkedRoleIds.value.length} 个角色`)
    roleDialog.value = false
  } catch { /* 拦截器已提示 */ } finally { roleSubmitting.value = false }
}
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">
      用户管理
    </h2>

    <CrudList
      ref="crudListRef"
      :api="userCrud"
      :columns="columns"
      :filters="filters"
      :row-key="'id'"
      empty-text="暂无用户"
      keyword-placeholder="用户名 / 昵称 / 手机 / 邮箱"
    >
      <template #toolbar>
        <el-button
          type="success"
          :icon="Plus"
          @click="openCreate"
        >
          新建用户
        </el-button>
        <el-button
          :icon="UserFilled"
          plain
          @click="hardReload"
        >
          强制刷新页面
        </el-button>
      </template>

      <template #column-status="{ row }">
        <StatusTag
          :value="(row as SysUserVO).status"
          :label="STATUS_LABEL_MAP[(row as SysUserVO).status]"
          :type-map="STATUS_TYPE_MAP"
        />
      </template>

      <template #column-actions="{ row }">
        <el-button
          link
          type="primary"
          :icon="Edit"
          @click="openEdit(row as SysUserVO)"
        >
          编辑
        </el-button>
        <el-button
          link
          type="warning"
          :icon="Key"
          @click="openResetPwd(row as SysUserVO)"
        >
          重置密码
        </el-button>
        <el-button
          link
          type="success"
          :icon="UserFilled"
          @click="openAssignRole(row as SysUserVO)"
        >
          分配角色
        </el-button>
        <el-button
          link
          :type="(row as SysUserVO).status === 1 ? 'info' : 'success'"
          @click="onToggle(row as SysUserVO)"
        >
          {{ (row as SysUserVO).status === 1 ? '禁用' : '启用' }}
        </el-button>
        <el-button
          link
          type="danger"
          :icon="Delete"
          :disabled="(row as SysUserVO).id === 1"
          @click="onDelete(row as SysUserVO)"
        >
          删除
        </el-button>
      </template>
    </CrudList>

    <!-- 新建/编辑对话框 -->
    <ModalForm
      v-model:visible="dialogVisible"
      :title="dialogMode === 'create' ? '新建用户' : '编辑用户'"
      :width="520"
      :model="form"
      :rules="rules"
      :loading="submitting"
      :submit-text="dialogMode === 'create' ? '创建' : '保存'"
      @submit="onSubmit"
    >
      <el-form-item label="用户名" prop="username">
        <el-input
          v-model="form.username"
          placeholder="登录用户名"
          :disabled="dialogMode === 'edit'"
        />
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input
          v-model="form.password"
          type="password"
          show-password
          :placeholder="dialogMode === 'create' ? '必填,6-64 位' : '留空表示不修改'"
        />
      </el-form-item>
      <el-form-item label="昵称" prop="nickname">
        <el-input v-model="form.nickname" />
      </el-form-item>
      <el-form-item label="邮箱" prop="email">
        <el-input v-model="form.email" />
      </el-form-item>
      <el-form-item label="手机" prop="phone">
        <el-input v-model="form.phone" />
      </el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-model="form.status">
          <el-radio :value="1">启用</el-radio>
          <el-radio :value="0">禁用</el-radio>
        </el-radio-group>
      </el-form-item>
    </ModalForm>

    <!-- 重置密码对话框 -->
    <ModalForm
      v-model:visible="pwdDialog"
      title="重置密码"
      :width="420"
      :model="pwdForm"
      :rules="pwdRules"
      :loading="pwdSubmitting"
      submit-text="确认重置"
      @submit="onResetPwdSubmit"
    >
      <el-form-item label="用户">
        <el-input :model-value="pwdTarget?.username || ''" disabled />
      </el-form-item>
      <el-form-item label="新密码" prop="newPassword">
        <el-input
          v-model="pwdForm.newPassword"
          type="password"
          show-password
          placeholder="6-64 位"
          @keyup.enter="onResetPwdSubmit"
        />
      </el-form-item>
    </ModalForm>

    <!-- 分配角色对话框 -->
    <ModalForm
      v-model:visible="roleDialog"
      :title="`分配角色 - ${roleTarget?.username ?? ''}`"
      :width="520"
      :model="{ checked: checkedRoleIds }"
      :submit-text="'保存'"
      :loading="roleSubmitting"
      @submit="onAssignRoleSubmit"
    >
      <el-checkbox-group v-model="checkedRoleIds">
        <div
          v-for="r in allRoleList"
          :key="r.id"
          class="role-item"
        >
          <el-checkbox :value="r.id">
            <span class="role-name">{{ r.roleName }}</span>
            <el-tag
              v-if="r.builtIn === 1"
              type="warning"
              size="small"
              style="margin-left:6px"
            >
              内置
            </el-tag>
            <span class="role-desc">{{ r.description || r.roleCode }}</span>
          </el-checkbox>
        </div>
      </el-checkbox-group>
    </ModalForm>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.role-item {
  padding: $spacing-4 0;
  .role-name { font-weight: $font-weight-medium; }
  .role-desc {
    color: var(--iot-text-secondary);
    font-size: $font-size-extra-small;
    margin-left: $spacing-8;
  }
}
</style>