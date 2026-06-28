<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Search, Refresh, View } from '@element-plus/icons-vue'
import {
  pageOperationLogs,
  getOperationLog,
  type SysOperationLogVO,
  type OperationLogQuery
} from '@/api/system/log'

const query = reactive<OperationLogQuery>({
  pageNum: 1, pageSize: 20,
  keyword: '', username: '', module: '', action: '',
  status: undefined, startTime: '', endTime: ''
})
const loading = ref(false)
const list = ref<SysOperationLogVO[]>([])
const total = ref(0)

const statusMap: Record<number, { label: string; type: string }> = {
  1: { label: '成功', type: 'success' },
  0: { label: '失败', type: 'danger' }
}

async function load() {
  loading.value = true
  try {
    const res: any = await pageOperationLogs(query)
    list.value = res.data?.records ?? []
    total.value = res.data?.total ?? 0
  } finally {
    loading.value = false
  }
}

function onSearch() { query.pageNum = 1; load() }
function onReset() {
  Object.assign(query, {
    pageNum: 1, keyword: '', username: '', module: '', action: '',
    status: undefined, startTime: '', endTime: ''
  })
  load()
}
function onPageChange(p: number) { query.pageNum = p; load() }
function onSizeChange(s: number) { query.pageSize = s; query.pageNum = 1; load() }

const detailVisible = ref(false)
const detail = ref<SysOperationLogVO | null>(null)
async function showDetail(row: SysOperationLogVO) {
  const res: any = await getOperationLog(row.id)
  detail.value = res.data ?? row
  detailVisible.value = true
}

const commonActions = ['登录', '登出', '新增', '修改', '删除', '查询', '导出']
const commonModules = ['用户管理', '角色管理', '设备管理', '产品管理', '工单管理', '告警管理', '知识库']

onMounted(load)
</script>

<template>
  <div
    v-loading="loading"
    class="page-container log-page"
  >
    <div class="page-header">
      <h2 class="page-title">
        操作日志
      </h2>
      <el-button
        :icon="Refresh"
        @click="load"
      >
        刷新
      </el-button>
    </div>

    <div class="page-card search-bar">
      <el-form
        :inline="true"
        @submit.prevent
      >
        <el-form-item label="用户名">
          <el-input
            v-model="query.username"
            placeholder="操作人"
            clearable
            style="width: 140px"
            @keyup.enter="onSearch"
          />
        </el-form-item>
        <el-form-item label="模块">
          <el-select
            v-model="query.module"
            placeholder="全部"
            clearable
            filterable
            style="width: 140px"
          >
            <el-option
              v-for="m in commonModules"
              :key="m"
              :label="m"
              :value="m"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="操作类型">
          <el-select
            v-model="query.action"
            placeholder="全部"
            clearable
            filterable
            style="width: 120px"
          >
            <el-option
              v-for="a in commonActions"
              :key="a"
              :label="a"
              :value="a"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="query.status"
            placeholder="全部"
            clearable
            style="width: 100px"
          >
            <el-option
              label="成功"
              :value="1"
            />
            <el-option
              label="失败"
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
          <el-button @click="onReset">
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="page-card">
      <el-table
        :data="list"
        stripe
        border
        empty-text="暂无日志"
      >
        <el-table-column
          prop="ts"
          label="时间"
          width="170"
        />
        <el-table-column
          prop="username"
          label="操作人"
          width="100"
        />
        <el-table-column
          prop="module"
          label="模块"
          width="120"
        />
        <el-table-column
          prop="action"
          label="操作"
          width="100"
        />
        <el-table-column
          prop="method"
          label="HTTP"
          width="80"
        />
        <el-table-column
          prop="url"
          label="请求路径"
          min-width="220"
          show-overflow-tooltip
        />
        <el-table-column
          prop="ip"
          label="IP"
          width="140"
        />
        <el-table-column
          label="耗时"
          width="90"
        >
          <template #default="{ row }">
            <span :class="{ 'text-warning': (row.costMs ?? 0) > 1000 }">{{ row.costMs ?? 0 }} ms</span>
          </template>
        </el-table-column>
        <el-table-column
          label="状态"
          width="80"
        >
          <template #default="{ row }">
            <el-tag
              :type="statusMap[row.status]?.type as any"
              size="small"
            >
              {{ statusMap[row.status]?.label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="80"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              size="small"
              :icon="View"
              @click="showDetail(row)"
            >
              详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :page-sizes="[20, 50, 100, 200]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="onPageChange"
          @size-change="onSizeChange"
        />
      </div>
    </div>

    <el-dialog
      v-model="detailVisible"
      title="日志详情"
      width="720px"
    >
      <el-descriptions
        v-if="detail"
        :column="2"
        border
      >
        <el-descriptions-item label="操作时间">
          {{ detail.ts }}
        </el-descriptions-item>
        <el-descriptions-item label="操作人">
          {{ detail.username }} (#{{ detail.userId }})
        </el-descriptions-item>
        <el-descriptions-item label="模块">
          {{ detail.module }}
        </el-descriptions-item>
        <el-descriptions-item label="操作">
          {{ detail.action }}
        </el-descriptions-item>
        <el-descriptions-item label="HTTP 方法">
          <el-tag
            size="small"
            :type="detail.method === 'GET' ? 'info' : 'primary'"
          >
            {{ detail.method }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusMap[detail.status]?.type as any">
            {{ statusMap[detail.status]?.label }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item
          label="请求路径"
          :span="2"
        >
          <code>{{ detail.url }}</code>
        </el-descriptions-item>
        <el-descriptions-item label="IP">
          {{ detail.ip }}
        </el-descriptions-item>
        <el-descriptions-item label="耗时">
          {{ detail.costMs }} ms
        </el-descriptions-item>
        <el-descriptions-item
          v-if="detail.userAgent"
          label="UA"
          :span="2"
        >
          <div class="text-secondary text-xs">
            {{ detail.userAgent }}
          </div>
        </el-descriptions-item>
        <el-descriptions-item
          label="请求参数"
          :span="2"
        >
          <pre class="json-block">{{ detail.params || '—' }}</pre>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.log-page { background: var(--iot-bg-page); }
.page-header { display: flex; align-items: center; gap: $spacing-12; margin-bottom: $spacing-16; .page-title { margin: 0; flex: 1; } }

.search-bar { margin-bottom: $spacing-12; padding: $spacing-16; :deep(.el-form-item) { margin-bottom: 0; } }
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: $spacing-16; }
.json-block {
  font-family: var(--iot-font-family-code);
  font-size: $font-size-extra-small;
  background: var(--iot-bg-page);
  padding: $spacing-8; border-radius: $radius-base;
  white-space: pre-wrap; word-break: break-all;
  margin: 0;
  max-height: 200px; overflow-y: auto;
}
</style>