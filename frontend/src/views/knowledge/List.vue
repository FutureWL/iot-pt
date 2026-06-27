<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete, Reading } from '@element-plus/icons-vue'
import {
  pageKnowledge,
  deleteKnowledge,
  type KnowledgeVO,
  type KnowledgeQuery
} from '@/api/knowledge'

const router = useRouter()
const query = reactive<KnowledgeQuery>({
  pageNum: 1, pageSize: 10, keyword: '',
  category: undefined, status: undefined
})
const loading = ref(false)
const list = ref<KnowledgeVO[]>([])
const total = ref(0)

const statusMap: Record<string, { label: string; type: string }> = {
  DRAFT: { label: '草稿', type: 'info' },
  PUBLISHED: { label: '已发布', type: 'success' },
  ARCHIVED: { label: '已归档', type: 'warning' }
}

const categories = ['故障处理', '巡检作业', '应急处置', '设备维护', '基础知识']

async function load() {
  loading.value = true
  try {
    const res: any = await pageKnowledge(query)
    list.value = res.data?.records ?? []
    total.value = res.data?.total ?? 0
  } finally {
    loading.value = false
  }
}

function onSearch() { query.pageNum = 1; load() }
function onReset() { query.keyword = ''; query.category = undefined; query.status = undefined; query.pageNum = 1; load() }
function onPageChange(p: number) { query.pageNum = p; load() }
function onSizeChange(s: number) { query.pageSize = s; query.pageNum = 1; load() }

function goCreate() { router.push('/knowledge/editor') }
function goEdit(row: KnowledgeVO) { router.push(`/knowledge/editor/${row.id}`) }

async function onDelete(row: KnowledgeVO) {
  await ElMessageBox.confirm(`确认删除文档「${row.title}」?`, '删除确认', { type: 'warning' })
  try {
    await deleteKnowledge(row.id)
    ElMessage.success('删除成功')
    load()
  } catch {}
}

onMounted(load)
</script>

<template>
  <div class="page-container kb-page">
    <div class="page-header">
      <h2 class="page-title">知识库</h2>
      <el-button type="primary" :icon="Plus" @click="goCreate">新建文档</el-button>
    </div>

    <div class="page-card search-bar">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="标题 / 标签" clearable style="width: 220px"
            :prefix-icon="Search" @keyup.enter="onSearch" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="query.category" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button :icon="Refresh" @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="page-card">
      <el-table v-loading="loading" :data="list" stripe border empty-text="暂无文档">
        <el-table-column prop="title" label="标题" min-width="240">
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="goEdit(row)">
              <el-icon><Reading /></el-icon> {{ row.title }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column prop="category" label="分类" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ row.category }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="summary" label="摘要" min-width="200" show-overflow-tooltip />
        <el-table-column prop="tags" label="标签" min-width="160">
          <template #default="{ row }">
            <el-tag v-for="tag in (row.tags ?? '').split(',').filter(Boolean)" :key="tag" size="small" type="info" class="mr-4">
              {{ tag }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="版本" width="80">
          <template #default="{ row }">v{{ row.version }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.status]?.type as any" size="small">
              {{ statusMap[row.status]?.label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="author" label="作者" width="100" />
        <el-table-column prop="updatedAt" label="更新时间" width="170" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="goEdit(row)">编辑</el-button>
            <el-button link type="danger" :icon="Delete" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
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
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.kb-page { background: var(--iot-bg-page); }
.page-header { display: flex; align-items: center; gap: $spacing-12; margin-bottom: $spacing-16; .page-title { margin: 0; flex: 1; } }
.mr-4 { margin-right: $spacing-4; }

.search-bar { margin-bottom: $spacing-12; padding: $spacing-16; :deep(.el-form-item) { margin-bottom: 0; } }
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: $spacing-16; }
</style>