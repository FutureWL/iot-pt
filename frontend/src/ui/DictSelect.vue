<script setup lang="ts" generic="T extends string | number = string | number">
/**
 * DictSelect — 字典下拉选择器
 *
 * 设计目标:把 useDict 字典加载 + <el-select> 选项渲染 整合成一行声明
 *
 * 使用示例:
 *   <DictSelect
 *     v-model="form.status"
 *     dict-type="workorder_status"
 *     placeholder="选择状态"
 *     clearable
 *   />
 *
 * 业务侧不再需要:
 *   - 写 useDict() 调用 + onMounted 加载
 *   - 维护一个 dictOptions ref
 *   - 在 <el-select> 里 v-for dictOptions
 *
 * 支持:
 *   - v-model:string | number 单值
 *   - multiple 多选
 *   - clearable / filterable / disabled 等透传
 *   - 字典加载失败不阻塞 UI(空选项 + 静默)
 *   - optionProps(item) 函数自定义每个 option 的额外 props(用于 :disabled 等)
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useDict } from '@/composables/useDict'

interface DictSelectItem {
  value: string
  label: string
}

interface Props {
  /** 字典类型编码,对应后端 GET /system/dict/{dictType} */
  dictType: string
  /** 当前值(v-model) */
  modelValue?: T | T[] | null
  /** 占位符 */
  placeholder?: string
  /** 可清空 */
  clearable?: boolean
  /** 可搜索 */
  filterable?: boolean
  /** 禁用 */
  disabled?: boolean
  /** 多选 */
  multiple?: boolean
  /** 自动加载,默认 true(mount 时拉取);false 时需要外部保证已加载 */
  autoLoad?: boolean
  /** 选择框宽度,默认 '100%' */
  width?: string
  /** 自定义每个 option 的额外 props(例如 :disabled 按值条件) */
  optionProps?: (item: DictSelectItem) => Record<string, unknown>
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: undefined,
  placeholder: '请选择',
  clearable: true,
  filterable: false,
  disabled: false,
  multiple: false,
  autoLoad: true,
  width: '100%',
  optionProps: undefined
})

const emit = defineEmits<{
  'update:modelValue': [v: T | T[] | null]
  'change': [v: T | T[] | null]
}>()

const { load, cache } = useDict()
const items = ref<DictSelectItem[]>([])

async function fetchDict() {
  // 已有缓存 → 立即同步
  const cached = cache.get(props.dictType)
  if (cached) {
    items.value = Object.entries(cached).map(([value, label]) => ({
      value, label: String(label)
    }))
    return
  }
  try {
    const map = await load(props.dictType)
    items.value = Object.entries(map).map(([value, label]) => ({
      value, label: String(label)
    }))
  } catch {
    // 静默失败:UI 仍可交互,只是没选项
  }
}

onMounted(() => {
  if (props.autoLoad) fetchDict()
})

// dictType 变化 → 重新加载
watch(() => props.dictType, () => {
  if (props.autoLoad) fetchDict()
})

// v-model → <el-select>
const selectValue = computed<T | T[] | null>({
  get: () => props.modelValue ?? (props.multiple ? [] : null),
  set: (v) => {
    emit('update:modelValue', v as T | T[] | null)
  }
})

function onChange(v: T | T[] | null) {
  // 透传 el-select 的 change 事件,父组件可以监听
  emit('change', v)
}
</script>

<template>
  <el-select
    v-model="selectValue"
    :placeholder="placeholder"
    :clearable="clearable"
    :filterable="filterable"
    :disabled="disabled"
    :multiple="multiple"
    :style="{ width }"
    @change="onChange"
  >
    <el-option
      v-for="item in items"
      :key="item.value"
      :label="item.label"
      :value="item.value"
      v-bind="optionProps ? optionProps(item) : {}"
    />
  </el-select>
</template>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;
</style>