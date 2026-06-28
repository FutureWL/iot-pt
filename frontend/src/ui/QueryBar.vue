<script setup lang="ts">
/**
 * QueryBar — 筛选条组件
 *
 * 设计目标:统一 6+ 处 el-form 筛选条(device/list, alert/center, workorder/list 等)
 *
 * 使用示例:
 *   <QueryBar :filters="filters" @search="onSearch" @reset="onReset" />
 *   const filters = [
 *     { prop: 'status', label: '状态', type: 'select', options: [...] },
 *     { prop: 'priority', label: '优先级', type: 'select', options: [...] }
 *   ]
 */
import { reactive } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'

export interface FilterItem {
  /** 字段名(对应 query 对象的 key) */
  prop: string
  /** 表单标签 */
  label: string
  /** 控件类型,默认 'input' */
  type?: 'input' | 'select'
  /** select 选项(type=select 时必填) */
  options?: Array<{ label: string; value: string | number }>
  /** 占位符 */
  placeholder?: string
}

interface Props {
  /** 筛选项定义 */
  filters: FilterItem[]
  /** 是否显示 keyword 搜索框,默认 true */
  searchable?: boolean
  /** keyword 占位符 */
  keywordPlaceholder?: string
}

const props = withDefaults(defineProps<Props>(), {
  searchable: true,
  keywordPlaceholder: '关键字搜索'
})

const emit = defineEmits<{
  search: [query: Record<string, unknown>]
  reset: []
}>()

// 表单本地状态:keyword + 各 filter 字段
const formState = reactive<Record<string, unknown>>({
  keyword: ''
})

// 初始化 filter 字段的初始值
for (const f of props.filters) {
  if (formState[f.prop] === undefined) {
    formState[f.prop] = f.type === 'select' ? '' : ''
  }
}

function onSearch(): void {
  emit('search', { ...formState })
}

function onReset(): void {
  formState.keyword = ''
  for (const f of props.filters) formState[f.prop] = ''
  emit('reset')
}
</script>

<template>
  <div class="query-bar">
    <el-form :inline="true" @submit.prevent>
      <el-form-item v-if="props.searchable" label="关键字">
        <el-input
          v-model="formState.keyword"
          :placeholder="props.keywordPlaceholder"
          clearable
          style="width: 220px"
        />
      </el-form-item>
      <el-form-item
        v-for="f in props.filters"
        :key="f.prop"
        :label="f.label"
      >
        <el-input
          v-if="f.type !== 'select'"
          v-model="formState[f.prop]"
          :placeholder="f.placeholder || `请输入${f.label}`"
          clearable
          style="width: 180px"
        />
        <el-select
          v-else
          v-model="formState[f.prop]"
          :placeholder="f.placeholder || `全部${f.label}`"
          clearable
          style="width: 180px"
        >
          <el-option
            v-for="opt in f.options ?? []"
            :key="String(opt.value)"
            :label="opt.label"
            :value="opt.value"
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
          @click="onReset"
        >
          重置
        </el-button>
        <slot name="extra" />
      </el-form-item>
    </el-form>
  </div>
</template>

<style scoped lang="scss">
.query-bar {
  :deep(.el-form-item) {
    margin-bottom: 0;
  }
}
</style>
