<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Search, Refresh, SetUp } from '@element-plus/icons-vue'
import {
  pageRules,
  createRule,
  updateRule,
  deleteRule,
  toggleRule,
  type IotRuleVO,
  type IotRuleDTO,
  type IotRuleQuery
} from '@/api/rule/rule'

const query = reactive<IotRuleQuery>({
  pageNum: 1, pageSize: 10, keyword: '',
  triggerType: '', status: undefined
})
const loading = ref(false)
const list = ref<IotRuleVO[]>([])
const total = ref(0)

const triggerTypes = [
  { value: 'data', label: '数据变化', desc: '设备任何属性上报时触发' },
  { value: 'property', label: '指定属性', desc: '指定属性变化时触发' },
  { value: 'event', label: '设备事件', desc: '设备主动上报事件' },
  { value: 'online', label: '上线', desc: '设备上线' },
  { value: 'offline', label: '离线', desc: '设备离线' }
]

async function load() {
  loading.value = true
  try {
    const res: any = await pageRules(query)
    list.value = res.data.records ?? []
    total.value = res.data.total ?? 0
  } finally {
    loading.value = false
  }
}
function onSearch() { query.pageNum = 1; load() }
function onReset() { query.keyword = ''; query.triggerType = ''; query.status = undefined; query.pageNum = 1; load() }
function onPageChange(p: number) { query.pageNum = p; load() }
function onSizeChange(s: number) { query.pageSize = s; query.pageNum = 1; load() }

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const submitting = ref(false)
const formRef = ref()

// 提供可视化模板供选择
const filterTemplates = [
  { label: '温度 > 30', expr: '{"property":"temperature","op":">","value":30}' },
  { label: '温度 > 30 且 湿度 < 20', expr: '{"allOf":[{"property":"temperature","op":">","value":30},{"property":"humidity","op":"<","value":20}]}' },
  { label: '电量 < 20', expr: '{"property":"battery","op":"<","value":20}' },
  { label: '任一(温度>30 或 电量<10)', expr: '{"anyOf":[{"property":"temperature","op":">","value":30},{"property":"battery","op":"<","value":10}]}' }
]
const actionTemplates = [
  { label: 'WARN 告警',
    actions: '[{"type":"alert","level":"WARN","title":"设备 ${deviceKey} 异常","content":"当前值: ${value}"}]' },
  { label: 'ERROR 告警',
    actions: '[{"type":"alert","level":"ERROR","title":"设备 ${deviceKey} 严重异常","content":"${identifier} = ${value}"}]' }
]

const form = reactive<IotRuleDTO>({
  id: undefined, ruleName: '', description: '',
  triggerType: 'data', filterExpr: '', actions: '', status: 1
})
const rules = {
  ruleName: [{ required: true, message: '请输入规则名', trigger: 'blur' }],
  triggerType: [{ required: true, message: '请选择触发器', trigger: 'change' }],
  filterExpr: [{ required: true, message: '请填写过滤条件', trigger: 'blur' }],
  actions: [{ required: true, message: '请填写动作', trigger: 'blur' }]
}

function openCreate() {
  dialogMode.value = 'create'
  Object.assign(form, {
    id: undefined, ruleName: '', description: '',
    triggerType: 'data', filterExpr: '', actions: '', status: 1
  })
  dialogVisible.value = true
}
function openEdit(row: IotRuleVO) {
  dialogMode.value = 'edit'
  Object.assign(form, JSON.parse(JSON.stringify(row)))
  dialogVisible.value = true
}

async function onSubmit() {
  if (!formRef.value) return
  let valid = false
  try { valid = await formRef.value.validate() } catch { valid = false }
  if (!valid) return
  // 校验 JSON
  try { JSON.parse(form.filterExpr) } catch { ElMessage.error('过滤条件不是合法 JSON'); return }
  try { JSON.parse(form.actions) } catch { ElMessage.error('动作不是合法 JSON'); return }

  submitting.value = true
  try {
    if (dialogMode.value === 'create') {
      await createRule(form)
      ElMessage.success('创建成功')
    } else {
      await updateRule(form)
      ElMessage.success('更新成功')
    }
    dialogVisible.value = false
    load()
  } catch {} finally { submitting.value = false }
}

async function onDelete(row: IotRuleVO) {
  await ElMessageBox.confirm(`确认删除规则「${row.ruleName}」?`, '删除确认', { type: 'warning' })
  try {
    await deleteRule(row.id)
    ElMessage.success('删除成功')
    load()
  } catch {}
}

async function onToggle(row: IotRuleVO) {
  const next = row.status === 1 ? 0 : 1
  try {
    await toggleRule(row.id, next)
    ElMessage.success(next ? '已启用' : '已停用')
    load()
  } catch {}
}

const previewVisible = ref(false)
const previewContent = ref('')
function showPreview(row: IotRuleVO) {
  previewContent.value = `过滤条件:\n${prettyJson(row.filterExpr)}\n\n动作:\n${prettyJson(row.actions)}`
  previewVisible.value = true
}
function prettyJson(s: string) {
  try { return JSON.stringify(JSON.parse(s), null, 2) } catch { return s }
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">规则列表</h2>

    <div class="page-card search-bar">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="规则名 / 描述" clearable style="width: 220px" @keyup.enter="onSearch" />
        </el-form-item>
        <el-form-item label="触发器">
          <el-select v-model="query.triggerType" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="t in triggerTypes" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
          <el-button :icon="Refresh" @click="onReset">重置</el-button>
          <el-button type="success" :icon="Plus" @click="openCreate">新建规则</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="page-card">
      <el-table v-loading="loading" :data="list" stripe border>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="ruleName" label="规则名" min-width="180" />
        <el-table-column label="触发器" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ triggerTypes.find(t => t.value === row.triggerType)?.label || row.triggerType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-button link type="info" @click="showPreview(row)">查看</el-button>
            <el-button link :type="row.status === 1 ? 'info' : 'success'" @click="onToggle(row)">
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button link type="danger" :icon="Delete" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无规则" /></template>
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
    <el-dialog v-model="dialogVisible" :title="dialogMode === 'create' ? '新建规则' : '编辑规则'"
      width="720px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" @submit.prevent>
        <el-form-item label="规则名" prop="ruleName">
          <el-input v-model="form.ruleName" placeholder="如 高温告警" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="触发器" prop="triggerType">
          <el-select v-model="form.triggerType" style="width: 100%">
            <el-option v-for="t in triggerTypes" :key="t.value" :label="`${t.label} (${t.desc})`" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="过滤条件" prop="filterExpr">
          <el-input v-model="form.filterExpr" type="textarea" :rows="4"
            placeholder='{"property":"temperature","op":">","value":30}' />
          <div class="hint">
            可选模板:
            <el-link v-for="t in filterTemplates" :key="t.label" type="primary" :underline="false"
              style="margin-right: 8px" @click="form.filterExpr = t.expr">{{ t.label }}</el-link>
          </div>
          <div class="hint">
            支持: <code>property</code> 属性名 / <code>op</code> 比较(>,&lt;,&gt;=,&lt;=,==,!=,contains) / <code>value</code> 比较值 / <code>allOf</code> AND / <code>anyOf</code> OR
          </div>
        </el-form-item>
        <el-form-item label="动作" prop="actions">
          <el-input v-model="form.actions" type="textarea" :rows="3"
            placeholder='[{"type":"alert","level":"WARN","title":"...","content":"..."}]' />
          <div class="hint">
            可选模板:
            <el-link v-for="t in actionTemplates" :key="t.label" type="primary" :underline="false"
              style="margin-right: 8px" @click="form.actions = t.actions">{{ t.label }}</el-link>
          </div>
          <div class="hint">
            支持变量: <code>${value}</code> <code>${identifier}</code> <code>${deviceKey}</code> <code>${productKey}</code> 以及所有当前属性值
          </div>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情预览 -->
    <el-dialog v-model="previewVisible" title="规则定义" width="600px">
      <pre class="preview-pre">{{ previewContent }}</pre>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.search-bar { margin-bottom: 12px; padding: 16px; :deep(.el-form-item) { margin-bottom: 0; } }
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 16px; }
.hint { color: #909399; font-size: 12px; margin-top: 4px; line-height: 1.6; }
.preview-pre {
  background: #1e1e1e; color: #d4d4d4; padding: 12px; border-radius: 6px;
  font-family: 'Menlo','Consolas',monospace; font-size: 12px; line-height: 1.5;
  max-height: 60vh; overflow: auto; margin: 0;
}
code { background: #f0f9eb; padding: 1px 4px; border-radius: 2px; font-family: 'Menlo',monospace; font-size: 11px; }
</style>