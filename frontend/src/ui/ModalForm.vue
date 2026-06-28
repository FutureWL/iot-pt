<script setup lang="ts" generic="T extends Record<string, unknown> = Record<string, unknown>">
/**
 * ModalForm — 表单对话框封装
 *
 * 设计目标:统一 13+ 处 <el-dialog> + <el-form> + 底部按钮 重复样板
 *
 * 使用示例:
 *   <ModalForm
 *     v-model:visible="dialogVisible"
 *     :title="dialogMode === 'create' ? '新建用户' : '编辑用户'"
 *     :model="form"
 *     :rules="rules"
 *     :loading="submitting"
 *     @submit="onSubmit"
 *   >
 *     <el-form-item label="用户名" prop="username">
 *       <el-input v-model="form.username" />
 *     </el-form-item>
 *     ...
 *   </ModalForm>
 *
 * 业务侧不再需要:
 *   - dialogVisible ref (由 v-model 管理)
 *   - formRef ref    (内部管理)
 *   - 写 <el-dialog> / <el-form> / 底部按钮 (组件接管)
 *   - 写 validate() 调用 (提交时自动校验,失败时不 emit submit)
 */
import { ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'

interface Props {
  /** 是否显示(v-model) */
  visible: boolean
  /** 弹窗标题 */
  title: string
  /** 表单数据对象 */
  model: T
  /** 校验规则(透传给 el-form) */
  rules?: FormRules
  /** 提交中状态(用于禁用按钮 + loading) */
  loading?: boolean
  /** 弹窗宽度,默认 640px */
  width?: string | number
  /** 提交按钮文字,默认 "保存" */
  submitText?: string
  /** 取消按钮文字,默认 "取消" */
  cancelText?: string
  /** 关闭后销毁 el-form 内部状态(默认 true,避免编辑时残留校验红框) */
  destroyOnClose?: boolean
  /** 点击遮罩关闭,默认 true */
  closeOnClickModal?: boolean
  /** 标签宽度,默认 '100px' */
  labelWidth?: string
  /** 关闭前钩子,返回 false 可阻止关闭(用于未保存确认) */
  beforeClose?: () => boolean | Promise<boolean>
}

const props = withDefaults(defineProps<Props>(), {
  rules: () => ({}),
  loading: false,
  width: 640,
  submitText: '保存',
  cancelText: '取消',
  destroyOnClose: true,
  closeOnClickModal: true,
  labelWidth: '100px',
  beforeClose: undefined
})

const emit = defineEmits<{
  'update:visible': [v: boolean]
  'submit': []
  'cancel': []
  'opened': []
  'closed': []
}>()

// 内部 formRef(对消费者隐藏)
const formRef = ref<FormInstance>()

async function onConfirm() {
  if (!formRef.value) return
  let valid = false
  try { valid = await formRef.value.validate() } catch { valid = false }
  if (!valid) return
  emit('submit')
}

function onCancelClick() {
  emit('cancel')
  emit('update:visible', false)
}

async function onDialogClose(done: () => void) {
  if (props.beforeClose) {
    const ok = await props.beforeClose()
    if (ok === false) return
  }
  emit('cancel')
  emit('update:visible', false)
  done()
}

function onOpened() { emit('opened') }
function onClosed() {
  // destroyOnClose=true 时 el-form 会重置校验状态;显式再 clearValidate 兜底
  try { formRef.value?.clearValidate() } catch { /* ignore */ }
  emit('closed')
}

// 关闭后清理校验状态(destroyOnClose 通常会处理,这里是双保险)
watch(() => props.visible, (v) => {
  if (!v) {
    try { formRef.value?.clearValidate() } catch { /* ignore */ }
  }
})

defineExpose({
  /** 手动触发校验(供父组件异步校验场景使用) */
  validate: async (): Promise<boolean> => {
    if (!formRef.value) return false
    try { return await formRef.value.validate() } catch { return false }
  },
  /** 手动清空校验 */
  clearValidate: () => formRef.value?.clearValidate(),
  /** 底层 formRef(高级场景,例如手动 resetFields) */
  formRef
})
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="title"
    :width="typeof width === 'number' ? `${width}px` : width"
    :close-on-click-modal="closeOnClickModal"
    :destroy-on-close="destroyOnClose"
    :before-close="onDialogClose"
    @update:model-value="(v: boolean) => emit('update:visible', v)"
    @opened="onOpened"
    @closed="onClosed"
  >
    <el-form
      ref="formRef"
      :model="model"
      :rules="rules"
      :label-width="labelWidth"
      @submit.prevent
    >
      <slot />
    </el-form>
    <template #footer>
      <el-button
        :disabled="loading"
        @click="onCancelClick"
      >
        {{ cancelText }}
      </el-button>
      <el-button
        type="primary"
        :loading="loading"
        @click="onConfirm"
      >
        {{ submitText }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;
</style>