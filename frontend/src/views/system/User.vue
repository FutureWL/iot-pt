<script setup lang="ts">
import { ref, reactive, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete, Key, UserFilled } from '@element-plus/icons-vue'
import {
  pageUsers,
  createUser,
  updateUser,
  deleteUser,
  resetPassword,
  toggleStatus,
  allRoles,
  type SysUserVO,
  type SysRoleVO,
  type UserDTO,
  type UserQuery
} from '@/api/system/user'
import { getUserRoleIds, assignUserRoles } from '@/api/system/role'

// ========== 查询条件 ==========
const query = reactive<UserQuery>({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  status: undefined
})

const loading = ref(false)
const list = ref<SysUserVO[]>([])
const total = ref(0)

// ========== 加载列表 ==========
async function load() {
  loading.value = true
  try {
    const res: any = await pageUsers(query)
    // 后端 MyBatis-Plus 返回 records,前端统一用 list
    list.value = res.data.records ?? []
    total.value = res.data.total ?? 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.pageNum = 1
  load()
}

function onResetSearch() {
  query.keyword = ''
  query.status = undefined
  query.pageNum = 1
  load()
}

/** 硬刷整个页面 — HMR 多轮更新后用这个清状态 */
function hardReload() {
  window.location.reload()
}

// ========== 新建/编辑对话框 ==========
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const formRef = ref()
const submitting = ref(false)

const form = reactive<UserDTO>({
  id: undefined,
  username: '',
  password: '',
  nickname: '',
  email: '',
  phone: '',
  status: 1
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 32, message: '长度 2-32', trigger: 'blur' }
  ],
  password: [
    {
      validator(_: any, value: string, cb: any) {
        if (dialogMode.value === 'create' && !value) {
          return cb(new Error('请输入密码'))
        }
        if (value && (value.length < 6 || value.length > 64)) {
          return cb(new Error('密码长度 6-64'))
        }
        cb()
      },
      trigger: 'blur'
    }
  ],
  email: [{ type: 'email' as const, message: '邮箱格式不正确', trigger: 'blur' }]
}

function openCreate() {
  dialogMode.value = 'create'
  Object.assign(form, {
    id: undefined,
    username: '',
    password: '',
    nickname: '',
    email: '',
    phone: '',
    status: 1
  })
  dialogVisible.value = true
}

function openEdit(row: SysUserVO) {
  dialogMode.value = 'edit'
  Object.assign(form, {
    id: row.id,
    username: row.username,
    password: '',
    nickname: row.nickname ?? '',
    email: row.email ?? '',
    phone: row.phone ?? '',
    status: row.status
  })
  dialogVisible.value = true
}

async function onSubmit() {
  if (!formRef.value) return
  // eslint-disable-next-line no-useless-assignment
  let valid = false
  try {
    valid = await formRef.value.validate()
  } catch {
    valid = false
  }
  if (!valid) return

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
    load()
  } catch (e: any) {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}

// ========== 删除 ==========
async function onDelete(row: SysUserVO) {
  await ElMessageBox.confirm(
    `确认删除用户「${row.username}」?该操作不可恢复`,
    '删除确认',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
  )
  try {
    await deleteUser(row.id)
    ElMessage.success('删除成功')
    load()
  } catch {}
}

// ========== 启/停 ==========
async function onToggle(row: SysUserVO) {
  const next = row.status === 1 ? 0 : 1
  const action = next === 1 ? '启用' : '禁用'
  await ElMessageBox.confirm(`确认${action}用户「${row.username}」?`, '提示', {
    type: 'warning'
  })
  try {
    await toggleStatus(row.id, next)
    ElMessage.success(`${action}成功`)
    load()
  } catch {}
}

// ========== 重置密码 ==========
const pwdDialog = ref(false)
const pwdSubmitting = ref(false)
// 用闭包变量保存当前选中的用户,不依赖 reactive 同步,避免被其他逻辑重置
let pwdTarget: SysUserVO | null = null
const pwdForm = reactive({ newPassword: '' })
const pwdFormRef = ref()

const pwdRules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度 6-64 位', trigger: 'blur' }
  ]
}

async function openResetPwd(row: SysUserVO) {
  if (!row || !row.id) {
    ElMessage.error('用户信息异常,无法重置密码')
    return
  }
  // 打开对话框前刷新列表,避免操作陈旧 id
  await load()
  const fresh = list.value.find((u) => u.id === row.id)
  if (!fresh) {
    ElMessage.warning('该用户已被删除,列表已刷新')
    return
  }
  pwdTarget = fresh
  pwdForm.newPassword = ''
  pwdDialog.value = true
}

async function onResetPwdSubmit() {
  if (!pwdFormRef.value) {
    ElMessage.error('表单未就绪')
    return
  }
  if (!pwdTarget || !pwdTarget.id) {
    ElMessage.error('未选中用户')
    return
  }

  // 用 Promise 形式的 validate,避开回调式异步语义陷阱
  // eslint-disable-next-line no-useless-assignment
  let valid = false
  try {
    valid = await pwdFormRef.value.validate()
  } catch {
    valid = false
  }
  if (!valid) return

  pwdSubmitting.value = true
  const username = pwdTarget.username
  const targetId = pwdTarget.id
  console.debug('[resetPwd] 开始请求', { id: targetId, username })

  try {
    const res: any = await resetPassword(targetId, pwdForm.newPassword)
    console.debug('[resetPwd] 后端响应', res)

    // 多渠道反馈 - 你绝对会看到其中一个
    ElNotification.success({
      title: '密码已重置',
      message: `用户「${username}」的密码已成功重置`,
      duration: 4000
    })
    ElMessage.success(`已重置「${username}」的密码`)

    pwdForm.newPassword = ''
    pwdDialog.value = false
    pwdTarget = null
  } catch (e: any) {
    console.error('[resetPwd] 失败', e)
    const msg = e?.message || '请求出错,请查看控制台'
    // 如果后端说用户不存在,极可能是列表陈旧,自动刷新一次
    if (msg.includes('用户不存在')) {
      ElMessage.warning('用户不存在,列表已自动刷新')
      await load()
    } else {
      ElNotification.error({
        title: '重置失败',
        message: msg,
        duration: 4000
      })
    }
  } finally {
    pwdSubmitting.value = false
  }
}

// ========== 分配角色 ==========
const roleDialog = ref(false)
const roleSubmitting = ref(false)
const roleTarget = ref<SysUserVO | null>(null)
const allRoleList = ref<SysRoleVO[]>([])
const checkedRoleIds = ref<number[]>([])

async function openAssignRole(row: SysUserVO) {
  if (!row.id) return
  await load()
  const fresh = list.value.find((u) => u.id === row.id)
  if (!fresh) {
    ElMessage.warning('该用户已被删除,列表已刷新')
    return
  }
  roleTarget.value = fresh
  // 取全部角色 + 当前用户已分配的角色 id
  const [allRes, idsRes]: any[] = await Promise.all([
    allRoles(),
    getUserRoleIds(fresh.id)
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
  } catch (e) {
    // 拦截器已提示
  } finally {
    roleSubmitting.value = false
  }
}

// ========== 分页 ==========
function onPageChange(p: number) {
  query.pageNum = p
  load()
}
function onSizeChange(s: number) {
  query.pageSize = s
  query.pageNum = 1
  load()
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">
      用户管理
    </h2>

    <!-- 搜索栏 -->
    <div class="page-card search-bar">
      <el-form
        :inline="true"
        @submit.prevent
      >
        <el-form-item label="关键字">
          <el-input
            v-model="query.keyword"
            placeholder="用户名 / 昵称 / 手机 / 邮箱"
            clearable
            style="width: 220px"
            @keyup.enter="onSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="query.status"
            placeholder="全部"
            clearable
            style="width: 120px"
          >
            <el-option
              label="启用"
              :value="1"
            />
            <el-option
              label="禁用"
              :value="0"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :icon="Search"
            @click="onSearch"
          >
            查询
          </el-button>
          <el-button
            :icon="Refresh"
            @click="onResetSearch"
          >
            重置
          </el-button>
          <el-button
            type="success"
            :icon="Plus"
            @click="openCreate"
          >
            新建用户
          </el-button>
          <el-button
            :icon="Refresh"
            plain
            @click="hardReload"
          >
            强制刷新页面
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 表格 -->
    <div class="page-card">
      <el-table
        v-loading="loading"
        :data="list"
        stripe
        border
      >
        <el-table-column
          prop="id"
          label="ID"
          width="80"
        />
        <el-table-column
          prop="username"
          label="用户名"
          min-width="120"
        />
        <el-table-column
          prop="nickname"
          label="昵称"
          min-width="120"
        />
        <el-table-column
          prop="email"
          label="邮箱"
          min-width="180"
        />
        <el-table-column
          prop="phone"
          label="手机"
          min-width="120"
        />
        <el-table-column
          label="状态"
          width="100"
        >
          <template #default="{ row }">
            <el-tag
              :type="row.status === 1 ? 'success' : 'info'"
              size="small"
            >
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="lastLoginAt"
          label="最后登录"
          width="170"
        />
        <el-table-column
          prop="createdAt"
          label="创建时间"
          width="170"
        />
        <el-table-column
          label="操作"
          width="320"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :icon="Edit"
              @click="openEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              link
              type="warning"
              :icon="Key"
              @click="openResetPwd(row)"
            >
              重置密码
            </el-button>
            <el-button
              link
              type="success"
              :icon="UserFilled"
              @click="openAssignRole(row)"
            >
              分配角色
            </el-button>
            <el-button
              link
              :type="row.status === 1 ? 'info' : 'success'"
              @click="onToggle(row)"
            >
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
            <el-button
              link
              type="danger"
              :icon="Delete"
              :disabled="row.id === 1"
              @click="onDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无用户" />
        </template>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="onPageChange"
          @size-change="onSizeChange"
        />
      </div>
    </div>

    <!-- 新建/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新建用户' : '编辑用户'"
      width="520px"
      destroy-on-close
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="80px"
      >
        <el-form-item
          label="用户名"
          prop="username"
        >
          <el-input
            v-model="form.username"
            placeholder="登录用户名"
            :disabled="dialogMode === 'edit'"
          />
        </el-form-item>
        <el-form-item
          label="密码"
          prop="password"
        >
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :placeholder="dialogMode === 'create' ? '必填,6-64 位' : '留空表示不修改'"
          />
        </el-form-item>
        <el-form-item
          label="昵称"
          prop="nickname"
        >
          <el-input v-model="form.nickname" />
        </el-form-item>
        <el-form-item
          label="邮箱"
          prop="email"
        >
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item
          label="手机"
          prop="phone"
        >
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">
              启用
            </el-radio>
            <el-radio :value="0">
              禁用
            </el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="onSubmit"
        >
          {{ dialogMode === 'create' ? '创建' : '保存' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 重置密码对话框 -->
    <el-dialog
      v-model="pwdDialog"
      title="重置密码"
      width="420px"
      destroy-on-close
    >
      <el-form
        ref="pwdFormRef"
        :model="pwdForm"
        :rules="pwdRules"
        label-width="80px"
        @submit.prevent
      >
        <el-form-item label="用户">
          <el-input
            :model-value="pwdTarget?.username || ''"
            disabled
          />
        </el-form-item>
        <el-form-item
          label="新密码"
          prop="newPassword"
        >
          <el-input
            v-model="pwdForm.newPassword"
            type="password"
            show-password
            placeholder="6-64 位"
            @keyup.enter="onResetPwdSubmit"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdDialog = false">
          取消
        </el-button>
        <el-button
          type="warning"
          :loading="pwdSubmitting"
          @click="onResetPwdSubmit"
        >
          确认重置
        </el-button>
      </template>
    </el-dialog>

    <!-- 分配角色对话框 -->
    <el-dialog
      v-model="roleDialog"
      width="520px"
      destroy-on-close
      :title="`分配角色 - ${roleTarget?.username ?? ''}`"
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
      <template #footer>
        <el-button @click="roleDialog = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="roleSubmitting"
          @click="onAssignRoleSubmit"
        >
          保存
        </el-button>
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