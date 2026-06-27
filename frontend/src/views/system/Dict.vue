<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Collection } from '@element-plus/icons-vue'
import {
  pageDictTypes,
  pageDictItems,
  createDictType,
  createDictItem,
  updateDictItem,
  deleteDictItem,
  deleteDictType,
  type SysDictTypeVO,
  type SysDictVO
} from '@/api/system/dict'

const loading = ref(false)
const activeType = ref<SysDictTypeVO | null>(null)

const typeQuery = reactive({ pageNum: 1, pageSize: 50 })
const types = ref<SysDictTypeVO[]>([])

const itemQuery = reactive({ pageNum: 1, pageSize: 10, type: '' })
const items = ref<SysDictVO[]>([])
const total = ref(0)

async function loadTypes() {
  const res: any = await pageDictTypes(typeQuery)
  types.value = res.data?.records ?? []
  if (types.value.length > 0 && !activeType.value) {
    activeType.value = types.value[0]
    itemQuery.type = activeType.value.type
    loadItems()
  }
}

async function loadItems() {
  if (!activeType.value) return
  itemQuery.type = activeType.value.type
  const res: any = await pageDictItems(itemQuery)
  items.value = res.data?.records ?? []
  total.value = res.data?.total ?? 0
}

async function load() {
  loading.value = true
  try { await loadTypes() } finally { loading.value = false }
}

function onSelectType(t: SysDictTypeVO) {
  activeType.value = t
  itemQuery.pageNum = 1
  loadItems()
}

function onPageChange(p: number) { itemQuery.pageNum = p; loadItems() }
function onSizeChange(s: number) { itemQuery.pageSize = s; itemQuery.pageNum = 1; loadItems() }

// 新建类型
const typeDialogVisible = ref(false)
const typeForm = ref<Partial<SysDictTypeVO>>({ type: '', typeName: '', description: '', status: 1 })
async function onCreateType() {
  try {
    await createDictType(typeForm.value)
    ElMessage.success('已创建')
    typeDialogVisible.value = false
    loadTypes()
  } catch {}
}

// 新建/编辑字典项
const itemDialogVisible = ref(false)
const itemDialogMode = ref<'create' | 'edit'>('create')
const itemFormRef = ref()
const itemForm = ref<Partial<SysDictVO>>({})

const itemRules = {
  code: [{ required: true, message: '请输入编码', trigger: 'blur' }],
  label: [{ required: true, message: '请输入显示名', trigger: 'blur' }],
  value: [{ required: true, message: '请输入值', trigger: 'blur' }]
}

function openCreateItem() {
  if (!activeType.value) { ElMessage.warning('请先选择字典类型'); return }
  itemDialogMode.value = 'create'
  itemForm.value = { type: activeType.value.type, code: '', label: '', value: '', sort: 0, status: 1 }
  itemDialogVisible.value = true
}

function openEditItem(row: SysDictVO) {
  itemDialogMode.value = 'edit'
  itemForm.value = { ...row }
  itemDialogVisible.value = true
}

async function onSaveItem() {
  if (!itemFormRef.value) return
  let valid = false
  try { valid = await itemFormRef.value.validate() } catch { valid = false }
  if (!valid) return
  try {
    if (itemDialogMode.value === 'edit') {
      await updateDictItem(itemForm.value)
      ElMessage.success('已更新')
    } else {
      await createDictItem(itemForm.value)
      ElMessage.success('已创建')
    }
    itemDialogVisible.value = false
    loadItems()
  } catch {}
}

async function onDeleteItem(row: SysDictVO) {
  try {
    await ElMessageBox.confirm(`确认删除字典项「${row.label}」?`, '删除确认', { type: 'warning' })
    await deleteDictItem(row.id)
    ElMessage.success('已删除')
    loadItems()
  } catch {}
}

async function onDeleteType(t: SysDictTypeVO) {
  try {
    await ElMessageBox.confirm(`确认删除字典类型「${t.typeName}」?所有项将一并删除。`, '删除确认', { type: 'warning' })
    await deleteDictType(t.id)
    ElMessage.success('已删除')
    if (activeType.value?.id === t.id) activeType.value = null
    loadTypes()
  } catch {}
}

onMounted(load)
</script>

<template>
  <div class="page-container dict-page" v-loading="loading">
    <div class="page-header">
      <h2 class="page-title">字典管理</h2>
      <el-button type="primary" :icon="Plus" @click="typeDialogVisible = true">新建字典类型</el-button>
    </div>

    <el-row :gutter="16">
      <el-col :xs="24" :md="8">
        <div class="page-card">
          <h3 class="card-title"><el-icon><Collection /></el-icon> 字典类型</h3>
          <div class="type-list">
            <div v-for="t in types" :key="t.id"
              class="type-item" :class="{ active: activeType?.id === t.id }"
              @click="onSelectType(t)">
              <div class="type-info">
                <div class="type-name">{{ t.typeName }}</div>
                <div class="type-code text-secondary text-xs">{{ t.type }}</div>
              </div>
              <el-button link type="danger" size="small" :icon="Delete" @click.stop="onDeleteType(t)" />
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
              <span v-if="activeType" class="text-secondary text-xs ml-8">({{ activeType.type }})</span>
            </h3>
            <el-button type="primary" size="small" :icon="Plus" @click="openCreateItem">新增字典项</el-button>
          </div>
          <el-table :data="items" stripe empty-text="暂无字典项">
            <el-table-column prop="code" label="编码" width="180">
              <template #default="{ row }">
                <el-tag size="small" type="info">{{ row.code }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="label" label="显示名" min-width="180" />
            <el-table-column prop="value" label="值" min-width="180" />
            <el-table-column prop="sort" label="排序" width="80" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                  {{ row.status === 1 ? '启用' : '禁用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="description" label="说明" min-width="160" show-overflow-tooltip />
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" :icon="Edit" @click="openEditItem(row)">编辑</el-button>
                <el-button link type="danger" size="small" :icon="Delete" @click="onDeleteItem(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrap">
            <el-pagination
              v-model:current-page="itemQuery.pageNum"
              v-model:page-size="itemQuery.pageSize"
              :page-sizes="[10, 20, 50]"
              :total="total"
              layout="total, sizes, prev, pager, next"
              @current-change="onPageChange"
              @size-change="onSizeChange" />
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 新建类型 -->
    <el-dialog v-model="typeDialogVisible" title="新建字典类型" width="500px" destroy-on-close>
      <el-form :model="typeForm" label-width="100px">
        <el-form-item label="类型编码" required>
          <el-input v-model="typeForm.type" placeholder="如 alert_level" />
        </el-form-item>
        <el-form-item label="类型名称" required>
          <el-input v-model="typeForm.typeName" placeholder="如 告警级别" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="typeForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="typeForm.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onCreateType">创建</el-button>
      </template>
    </el-dialog>

    <!-- 新建/编辑字典项 -->
    <el-dialog v-model="itemDialogVisible" :title="itemDialogMode === 'edit' ? '编辑字典项' : '新增字典项'"
      width="500px" destroy-on-close>
      <el-form ref="itemFormRef" :model="itemForm" :rules="itemRules" label-width="100px">
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
          <el-input-number v-model="itemForm.sort" :min="0" controls-position="right" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="itemForm.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="itemForm.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSaveItem">保存</el-button>
      </template>
    </el-dialog>
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

.pagination-wrap { display: flex; justify-content: flex-end; margin-top: $spacing-16; }
</style>