<script setup lang="ts">
/**
 * EmptyState — 空状态组件
 *
 * 设计目标:统一全站 25 处 <el-empty> 用法,提供可选操作按钮
 *
 * 使用示例:
 *   <EmptyState />
 *   <EmptyState text="暂无设备" />
 *   <EmptyState text="暂无数据" action-text="新建" @action="openCreate" />
 */
interface Props {
  /** 空状态文案,默认 '暂无数据' */
  text?: string
  /** el-empty 内置图片类型 */
  image?: 'noData' | 'noPermission' | 'noSearch'
  /** 操作按钮文案,设置后渲染按钮 */
  actionText?: string
}

const props = withDefaults(defineProps<Props>(), {
  text: '暂无数据',
  image: 'noData',
  actionText: ''
})

const emit = defineEmits<{ action: [] }>()

function onAction(): void {
  emit('action')
}
</script>

<template>
  <el-empty :description="props.text" :image="props.image">
    <template v-if="props.actionText" #default>
      <el-button
        type="primary"
        data-testid="empty-action"
        @click="onAction"
      >
        {{ props.actionText }}
      </el-button>
    </template>
  </el-empty>
</template>
