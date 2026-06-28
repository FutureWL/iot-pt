<script setup lang="ts">
/**
 * 规则列表 — <CrudList> 重构版 (474 → ~250 行)
 *
 * 设计要点:
 *   - 列表/筛选/分页 由 <CrudList> 接管
 *   - "新建/编辑"对话框保留(JSON 编辑器 + 模板按钮)
 *   - "预览"对话框保留
 *   - 状态用 <StatusTag>(0=停用/danger, 1=启用/success)
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, View, SetUp } from '@element-plus/icons-vue'
import {
  ruleCrud,
  createRule,
  updateRule,
  toggleRule,
  type IotRuleVO,
  type IotRuleDTO,
  type IotRuleQuery
} from '@/api/rule/rule'
import { CrudList, StatusTag, type ColumnDef, type FilterItem, type StatusType } from '@/ui'

// ========== 列表 ==========
const triggerTypeOptions = [
  { value: 'data', label: '数据变化' },
  { value: 'property', label: '指定属性' },
  { value: 'event', label: '设备事件' },
  { value: 'online', label: '上线' },
  { value: 'offline', label: '离线' }
]
const triggerTypeLabel = Object.fromEntries(triggerTypeOptions.map(t => [t.value, t.label]))

const filters: FilterItem[] = [
  { prop: 'triggerType', label: '触发器', type: 'select', options: triggerTypeOptions },
  {
    prop: 'status',
    label: '状态',
    type: 'select',
    options: [
      { label: '启用', value: 1 },
      { label: '停用', value: 0 }
    ]
  }
]

const columns: ColumnDef<IotRuleVO>[] = [
  { prop: 'id', label: 'ID', width: 80 },
  { prop: 'ruleName', label: '规则名', minWidth: 180 },
  { prop: 'description', label: '描述', minWidth: 200, showOverflowTooltip: true },
  { prop: 'triggerType', label: '触发器', width: 110, slot: 'triggerType' },
  { prop: 'status', label: '状态', width: 90, slot: 'status' },
  { prop: 'updatedAt', label: '更新时间', width: 170 },
  { label: '操作', width: 280, fixed: 'right', slot: 'actions' }
]

const STATUS_TYPE_MAP: Record<string, StatusType> = { '0': 'danger', '1': 'success' }
const STATUS_LABEL_MAP: Record<number, string> = { 0: '停用', 1: '启用' }

const crudListRef = ref<{ refresh: () => Promise<void> } | null>(null)
function refresh(): void { void crudListRef.value?.refresh() }

// ========== 新建/编辑对话框 ==========
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const submitting = ref(false)
const formRef = ref()

const filterTemplates = [
  { label: '温度 > 30', expr: '{"property":"temperature","op":">","value":30}' },
  { label: '温度 > 30 且 湿度 < 20', expr: '{"allOf":[{"property":"temperature","op":">","value":30},{"property":"humidity","op":"<","value":20}]}' },
  { label: '电量 < 20', expr: '{"property":"battery","op":"<","value":20}' },
  { label: '任一(温度>30 或 电量<10)', expr: '{"anyOf":[{"property":"temperature","op":">","value":30},{"property":"battery","op":"<","value":10}]}' }
]
const actionTemplates = [
  { label: 'WARN 告警', actions: '[{"type":"alert","level":"WARN","title":"设备 ${deviceKey} 异常","content":"当前值: ${value}"}]' },
  { label: 'ERROR 告警', actions: '[{"type":"alert","level":"ERROR","title":"设备 ${deviceKey} 严重异常","content":"${identifier} = ${value}"}]' }
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
    refresh()
  } catch { /* ignore */ } finally { submitting.value = false }
}

async function onDelete(row: IotRuleVO) {
  await ElMessageBox.confirm(`确认删除规则「${row.ruleName}」?`, '删除确认', { type: 'warning' })
  try {
    await ruleCrud.remove!(row.id)
    ElMessage.success('删除成功')
    refresh()
  } catch { /* ignore */ }
}

async function onToggle(row: IotRuleVO) {
  const next = row.status === 1 ? 0 : 1
  try {
    await toggleRule(row.id, next)
    ElMessage.success(next ? '已启用' : '已停用')
    refresh()
  } catch { /* ignore */ }
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
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">
      规则列表
    </h2>

    <CrudList
      ref="crudListRef"
      :api="ruleCrud"
      :columns="columns"
      :filters="filters"
      :row-key="'id'"
      empty-text="暂无规则"
      keyword-placeholder="规则名 / 描述"
    >
      <template #toolbar>
        <el-button
          type="success"
          :icon="Plus"
          @click="openCreate"
        >
          新建规则
        </el-button>
      </template>

      <template #column-triggerType="{ row }">
        <el-tag size="small">
          {{ triggerTypeLabel[(row as IotRuleVO).triggerType] || (row as IotRuleVO).triggerType }}
        </el-tag>
      </template>

      <template #column-status="{ row }">
        <StatusTag
          :value="(row as IotRuleVO).status"
          :label="STATUS_LABEL_MAP[(row as IotRuleVO).status]"
          :type-map="STATUS_TYPE_MAP"
        />
      </template>

      <template #column-actions="{ row }">
        <el-button
          link
          type="primary"
          :icon="View"
          @click="showPreview(row as IotRuleVO)"
        >
          预览
        </el-button>
        <el-button
          link
          type="primary"
          :icon="Edit"
          @click="openEdit(row as IotRuleVO)"
        >
          编辑
        </el-button>
        <el-button
          link
          :type="(row as IotRuleVO).status === 1 ? 'warning' : 'success'"
          :icon="SetUp"
          @click="onToggle(row as IotRuleVO)"
        >
          {{ (row as IotRuleVO).status === 1 ? '停用' : '启用' }}
        </el-button>
        <el-button
          link
          type="danger"
          :icon="Delete"
          @click="onDelete(row as IotRuleVO)"
        >
          删除
        </el-button>
      </template>
    </CrudList>

    <!-- 新建/编辑对话框(逻辑复杂:JSON 模板) -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新建规则' : '编辑规则'"
      width="720px"
      destroy-on-close
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
        @submit.prevent
      >
        <el-form-item label="规则名" prop="ruleName">
          <el-input v-model="form.ruleName" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="触发器" prop="triggerType">
          <el-select v-model="form.triggerType" style="width: 100%">
            <el-option
              v-for="t in triggerTypeOptions"
              :key="t.value"
              :label="t.label"
              :value="t.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="过滤条件" prop="filterExpr">
          <el-input
            v-model="form.filterExpr"
            type="textarea"
            :rows="4"
            placeholder='JSON 格式,如 {"property":"temperature","op":">","value":30}'
          />
          <div class="template-row">
            <span class="hint">模板:</span>
            <el-button
              v-for="t in filterTemplates"
              :key="t.label"
              link
              type="primary"
              @click="form.filterExpr = t.expr"
            >
              {{ t.label }}
            </el-button>
          </div>
        </el-form-item>
        <el-form-item label="动作" prop="actions">
          <el-input
            v-model="form.actions"
            type="textarea"
            :rows="4"
            placeholder='JSON 数组,如 [{"type":"alert","level":"WARN","title":"..."}]'
          />
          <div class="template-row">
            <span class="hint">模板:</span>
            <el-button
              v-for="t in actionTemplates"
              :key="t.label"
              link
              type="primary"
              @click="form.actions = t.actions"
            >
              {{ t.label }}
            </el-button>
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
        <el-button type="primary" :loading="submitting" @click="onSubmit">
          {{ dialogMode === 'create' ? '创建' : '保存' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 预览对话框 -->
    <el-dialog
      v-model="previewVisible"
      title="规则预览"
      width="640px"
    >
      <pre class="preview">{{ previewContent }}</pre>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.template-row {
  display: flex; flex-wrap: wrap; gap: $spacing-8; align-items: center;
  margin-top: $spacing-8;
}
.hint { color: var(--iot-text-secondary); font-size: $font-size-extra-small; }
.preview {
  background: var(--iot-bg-hover);
  padding: $spacing-12;
  border-radius: $radius-base;
  font-family: var(--iot-font-family-code);
  font-size: $font-size-small;
  white-space: pre-wrap;
}
</style>