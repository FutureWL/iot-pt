<script setup lang="ts">
/**
 * 知识库列表 — 演示 CrudList 重构(265 行 → ~80 行)
 */
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Reading } from '@element-plus/icons-vue'
import {
  knowledgeCrud,
  deleteKnowledge,
  type KnowledgeVO
} from '@/api/knowledge'
import { CrudList, StatusTag } from '@/ui'
import type { ColumnDef, FilterItem } from '@/ui'

const router = useRouter()

const filters: FilterItem[] = [
  {
    prop: 'category',
    label: '分类',
    type: 'select',
    options: ['故障处理', '巡检作业', '应急处置', '设备维护', '基础知识'].map((c) => ({ label: c, value: c }))
  },
  {
    prop: 'status',
    label: '状态',
    type: 'select',
    options: [
      { label: '草稿', value: 'DRAFT' },
      { label: '已发布', value: 'PUBLISHED' },
      { label: '已归档', value: 'ARCHIVED' }
    ]
  }
]

const columns: ColumnDef<KnowledgeVO>[] = [
  { prop: 'title', label: '标题', minWidth: 240, slot: 'title' },
  { prop: 'category', label: '分类', width: 120, slot: 'category' },
  { prop: 'summary', label: '摘要', minWidth: 200, showOverflowTooltip: true },
  { prop: 'tags', label: '标签', minWidth: 160, slot: 'tags' },
  { label: '版本', width: 80, slot: 'version' },
  { prop: 'status', label: '状态', width: 100, slot: 'status' },
  { prop: 'author', label: '作者', width: 100 },
  { prop: 'updatedAt', label: '更新时间', width: 170 },
  { label: '操作', width: 160, fixed: 'right', slot: 'actions' }
]

function goCreate(): void {
  router.push('/knowledge/editor')
}

function goEdit(row: KnowledgeVO): void {
  router.push(`/knowledge/editor/${row.id}`)
}

async function onDelete(row: KnowledgeVO): Promise<void> {
  await ElMessageBox.confirm(`确认删除文档「${row.title}」?`, '删除确认', { type: 'warning' })
  try {
    await deleteKnowledge(row.id)
    ElMessage.success('删除成功')
    crudListRef.value?.refresh()
  } catch {}
}

const crudListRef = ref<{ refresh: () => Promise<void> } | null>(null)
</script>

<template>
  <div class="kb-page page-container">
    <div class="page-header">
      <h2 class="page-title">
        知识库
      </h2>
    </div>

    <CrudList
      ref="crudListRef"
      :api="knowledgeCrud"
      :columns="columns"
      :filters="filters"
      :row-key="'id'"
      empty-text="暂无文档"
      keyword-placeholder="标题 / 标签"
    >
      <template #toolbar>
        <el-button
          type="primary"
          :icon="Plus"
          @click="goCreate"
        >
          新建文档
        </el-button>
      </template>

      <template #column-title="{ row }">
        <el-link
          type="primary"
          :underline="false"
          @click="goEdit(row as KnowledgeVO)"
        >
          <el-icon><Reading /></el-icon> {{ (row as KnowledgeVO).title }}
        </el-link>
      </template>

      <template #column-category="{ row }">
        <el-tag size="small">
          {{ (row as KnowledgeVO).category }}
        </el-tag>
      </template>

      <template #column-tags="{ row }">
        <el-tag
          v-for="tag in ((row as KnowledgeVO).tags ?? '').split(',').filter(Boolean)"
          :key="tag"
          size="small"
          type="info"
          class="mr-4"
        >
          {{ tag }}
        </el-tag>
      </template>

      <template #column-version="{ row }">
        v{{ (row as KnowledgeVO).version }}
      </template>

      <template #column-status="{ row }">
        <StatusTag :value="(row as KnowledgeVO).status" />
      </template>

      <template #column-actions="{ row }">
        <el-button
          link
          type="primary"
          :icon="Edit"
          @click="goEdit(row as KnowledgeVO)"
        >
          编辑
        </el-button>
        <el-button
          link
          type="danger"
          :icon="Delete"
          @click="onDelete(row as KnowledgeVO)"
        >
          删除
        </el-button>
      </template>
    </CrudList>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.kb-page { background: var(--iot-bg-page); }
.page-header { display: flex; align-items: center; gap: $spacing-12; margin-bottom: $spacing-16; .page-title { margin: 0; flex: 1; } }
.mr-4 { margin-right: $spacing-4; }
</style>
