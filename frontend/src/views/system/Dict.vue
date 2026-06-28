<script setup lang="ts">
/**
 * 字典管理 — <CrudList> + <ModalForm> 重构版 (464 → ~330 行)
 *
 * 设计要点:
 *   - 左侧: 字典类型列表(自定义选中交互,保留内联)
 *   - 右侧: 字典项 CRUD → <CrudList> + <ModalForm>
 *   - 两个对话框(新建类型 / 字典项增删改)都用 ModalForm 统一
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Collection } from '@element-plus/icons-vue'
import {
  pageDictTypes,
  dictItemCrud,
  createDictType,
  createDictItem,
  updateDictItem,
  deleteDictType,
  type SysDictTypeVO,
  type SysDictVO
} from '@/api/system/dict'
import { CrudList, ModalForm, type ColumnDef, type FilterItem, type StatusType } from '@/ui'

// ========== 左侧:字典类型 ==========
const loading = ref(false)
const activeType = ref<SysDictTypeVO | null>(null)
const typeQuery = reactive({ pageNum: 1, pageSize: 50 })
const types = ref<SysDictTypeVO[]>([])

async function loadTypes() {
  const res: any = await pageDictTypes(typeQuery)
  types.value = res.data?.records ?? []
  if (types.value.length > 0 && !activeType.value) {
    selectType(types.value[0]!)
  }
}

async function load() {
  loading.value = true
  try { await loadTypes() } finally { loading.value = false }
}

function selectType(t: SysDictTypeVO): void {
  activeType.value = t
}

onMounted(load)

// ========== 右侧:字典项 CRUD ==========
const crudListRef = ref<{ refresh: () => Promise<void> } | null>(null)
function refresh(): void { void crudListRef.value?.refresh() }

const columns: ColumnDef<SysDictVO>[] = [
  { prop: 'code', label: '编码', minWidth: 160, slot: 'code' },
  { prop: 'label', label: '显示名', minWidth: 160 },
  { prop: 'value', label: '值', minWidth: 160 },
  { prop: 'sort', label: '排序', width: 80 },
  { prop: 'status', label: '状态', width: 100, slot: 'status' },
  { prop: 'description', label: '说明', minWidth: 180, showOverflowTooltip: true },
  { label: '操作', width: 160, fixed: 'right', slot: 'actions' }
]

const STATUS_TYPE_MAP: Record<string, StatusType> = { '0': 'info', '1': 'success' }
const STATUS_LABEL_MAP: Record<number, string> = { 0: '禁用', 1: '启用' }

// 字典项筛选:增加 type(由左侧选中触发)
const dictItemFilters = computed<FilterItem[]>(() => activeType.value
  ? [{ prop: 'type', label: '类型', type: 'select', options: [{ label: activeType.value.typeName, value: activeType.value.type }] }]
  : [])

// ========== 新建类型对话框 ==========
const typeDialogVisible = ref(false)
const typeForm = reactive<Partial<SysDictTypeVO>>({
  type: '', typeName: '', description: '', status: 1
})
const typeRules = {
  type: [{ required: true, message: '请输入类型编码', trigger: 'blur' }],
  typeName: [{ required: true, message: '请输入类型名称', trigger: 'blur' }]
}
async function onCreateType() {
  try {
    await createDictType(typeForm)
    ElMessage.success('已创建')
    typeDialogVisible.value = false
    await loadTypes()
    // 自动选中新创建的类型
    if (types.value.length > 0) selectType(types.value[types.value.length - 1]!)
  } catch { /* 拦截器已提示 */ }
}

async function onDeleteType(t: SysDictTypeVO) {
  await ElMessageBox.confirm(
    `确认删除字典类型「${t.typeName}」?所有项将一并删除。`,
    '删除确认', { type: 'warning' }
  )
  try {
    await deleteDictType(t.id)
    ElMessage.success('已删除')
    if (activeType.value?.id === t.id) activeType.value = null
    await loadTypes()
    if (types.value.length > 0 && !activeType.value) selectType(types.value[0]!)
  } catch { /* ignore */ }
}

// ========== 字典项 CRUD 对话框 ==========
const itemDialogVisible = ref(false)
const itemDialogMode = ref<'create' | 'edit'>('create')
const itemSubmitting = ref(false)
const itemForm = reactive<Partial<SysDictVO>>({})
const itemRules = {
  code: [{ required: true, message: '请输入编码', trigger: 'blur' }],
  label: [{ required: true, message: '请输入显示名', trigger: 'blur' }],
  value: [{ required: true, message: '请输入值', trigger: 'blur' }]
}

function openCreateItem() {
  if (!activeType.value) {
    ElMessage.warning('请先选择字典类型')
    return
  }
  itemDialogMode.value = 'create'
  Object.assign(itemForm, {
    type: activeType.value.type, code: '', label: '', value: '',
    sort: 0, status: 1, description: ''
  })
  itemDialogVisible.value = true
}

function openEditItem(row: SysDictVO) {
  itemDialogMode.value = 'edit'
  Object.assign(itemForm, row)
  itemDialogVisible.value = true
}

async function onSaveItem() {
  itemSubmitting.value = true
  try {
    if (itemDialogMode.value === 'edit') {
      await updateDictItem(itemForm)
      ElMessage.success('已更新')
    } else {
      await createDictItem(itemForm)
      ElMessage.success('已创建')
    }
    itemDialogVisible.value = false
    refresh()
  } catch { /* 拦截器已提示 */ } finally { itemSubmitting.value = false }
}

async function onDeleteItem(row: SysDictVO) {
  await ElMessageBox.confirm(`确认删除字典项「${row.label}」?`, '删除确认', { type: 'warning' })
  try {
    await dictItemCrud.remove!(row.id)
    ElMessage.success('已删除')
    refresh()
  } catch { /* ignore */ }
}
</script>

<template>
  <div
    v-loading="loading"
    class="page-container dict-page"
  >
    <div class="page-header">
      <h2 class="page-title">
        字典管理
      </h2>
      <el-button
        type="primary"
        :icon="Plus"
        @click="typeDialogVisible = true"
      >
        新建字典类型
      </el-button>
    </div>

    <el-row :gutter="16">
      <el-col :xs="24" :md="8">
        <div class="page-card">
          <h3 class="card-title">
            <el-icon><Collection /></el-icon> 字典类型
          </h3>
          <div class="type-list">
            <div
              v-for="t in types"
              :key="t.id"
              class="type-item"
              :class="{ active: activeType?.id === t.id }"
              @click="selectType(t)"
            >
              <div class="type-info">
                <div class="type-name">{{ t.typeName }}</div>
                <div class="type-code text-secondary text-xs">{{ t.type }}</div>
              </div>
              <el-button
                link
                type="danger"
                size="small"
                :icon="Delete"
                @click.stop="onDeleteType(t)"
              />
            </div>
            <el-empty v-if="types.length === 0" description="暂无字典类型" />
          </div>
        </div>
      </el-col>

      <el-col :xs="24" :md="16">
        <div class="page-card">
          <div class="page-toolbar">
            <h3 class="card-title-inline">
              {{ activeType?.typeName ?? '字典项' }}
              <span
                v-if="activeType"
                class="text-secondary text-xs ml-8"
              >({{ activeType.type }})</span>
            </h3>
            <el-button
              type="primary"
              size="small"
              :icon="Plus"
              :disabled="!activeType"
              @click="openCreateItem"
            >
              新增字典项
            </el-button>
          </div>

          <CrudList
            v-if="activeType"
            ref="crudListRef"
            :key="activeType.id"
            :api="dictItemCrud"
            :columns="columns"
            :filters="dictItemFilters"
            :initial-query="{ type: activeType.type }"
            :row-key="'id'"
            empty-text="暂无字典项"
            keyword-placeholder="编码 / 显示名 / 值"
          >
            <template #column-code="{ row }">
              <el-tag size="small" type="info">{{ (row as SysDictVO).code }}</el-tag>
            </template>

            <template #column-status="{ row }">
              <StatusTag
                :value="(row as SysDictVO).status ?? 0"
                :label="STATUS_LABEL_MAP[(row as SysDictVO).status ?? 0]"
                :type-map="STATUS_TYPE_MAP"
              />
            </template>

            <template #column-actions="{ row }">
              <el-button
                link
                type="primary"
                size="small"
                :icon="Edit"
                @click="openEditItem(row as SysDictVO)"
              >
                编辑
              </el-button>
              <el-button
                link
                type="danger"
                size="small"
                :icon="Delete"
                @click="onDeleteItem(row as SysDictVO)"
              >
                删除
              </el-button>
            </template>
          </CrudList>
          <el-empty v-else description="请先选择左侧字典类型" />
        </div>
      </el-col>
    </el-row>

    <!-- 新建字典类型 -->
    <ModalForm
      v-model:visible="typeDialogVisible"
      title="新建字典类型"
      :width="500"
      :model="typeForm"
      :rules="typeRules"
      submit-text="创建"
      @submit="onCreateType"
    >
      <el-form-item label="类型编码" prop="type">
        <el-input v-model="typeForm.type" placeholder="如 alert_level" />
      </el-form-item>
      <el-form-item label="类型名称" prop="typeName">
        <el-input v-model="typeForm.typeName" placeholder="如 告警级别" />
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="typeForm.description" type="textarea" :rows="2" />
      </el-form-item>
      <el-form-item label="状态">
        <el-switch
          v-model="typeForm.status"
          :active-value="1"
          :inactive-value="0"
        />
      </el-form-item>
    </ModalForm>

    <!-- 字典项新增/编辑 -->
    <ModalForm
      v-model:visible="itemDialogVisible"
      :title="itemDialogMode === 'edit' ? '编辑字典项' : '新增字典项'"
      :width="500"
      :model="itemForm"
      :rules="itemRules"
      :loading="itemSubmitting"
      submit-text="保存"
      @submit="onSaveItem"
    >
      <el-form-item label="编码" prop="code">
        <el-input v-model="itemForm.code" />
      </el-form-item>
      <el-form-item label="显示名" prop="label">
        <el-input v-model="itemForm.label" />
      </el-form-item>
      <el-form-item label="值" prop="value">
        <el-input v-model="itemForm.value" />
      </el-form-item>
      <el-form-item label="排序">
        <el-input-number
          v-model="itemForm.sort"
          :min="0"
          controls-position="right"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-switch
          v-model="itemForm.status"
          :active-value="1"
          :inactive-value="0"
        />
      </el-form-item>
      <el-form-item label="说明">
        <el-input
          v-model="itemForm.description"
          type="textarea"
          :rows="2"
        />
      </el-form-item>
    </ModalForm>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.dict-page { background: var(--iot-bg-page); }
.page-header { display: flex; align-items: center; gap: $spacing-12; margin-bottom: $spacing-16; .page-title { margin: 0; flex: 1; } }
.ml-8 { margin-left: $spacing-8; }

.card-title {
  font-size: $font-size-medium; margin: 0 0 $spacing-16;
  color: var(--iot-text-primary); display: flex; align-items: center; gap: $spacing-8;
  &::before { content: ''; display: block; width: 3px; height: 14px; background: var(--iot-color-primary); }
}
.card-title-inline { font-size: $font-size-medium; margin: 0; color: var(--iot-text-primary); flex: 1; }

.type-list { display: flex; flex-direction: column; gap: $spacing-4; max-height: 600px; overflow-y: auto; }
.type-item {
  display: flex; align-items: center; gap: $spacing-8;
  padding: $spacing-8 $spacing-12; border-radius: $radius-base;
  cursor: pointer; border: 1px solid var(--iot-border-lighter);
  transition: all $transition-fast;
  &:hover { background: var(--iot-bg-hover); border-color: var(--iot-color-primary-light-5); }
  &.active { background: var(--iot-color-primary-light-9); border-color: var(--iot-color-primary); }
}
.type-info { flex: 1; min-width: 0; }
.type-name { font-weight: $font-weight-medium; color: var(--iot-text-primary); }

.text-secondary { color: var(--iot-text-regular); }
.text-xs { font-size: $font-size-extra-small; }
</style>