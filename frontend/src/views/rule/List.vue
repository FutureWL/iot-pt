<script setup lang="ts">
defineProps<{ title?: string }>()
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">规则列表</h2>
    <div class="page-card" style="min-height: 60vh; display: flex; align-items: center; justify-content: center;">
      <el-empty description="SpEL 规则编辑 + 动作配置(M5 后填充)" />
    </div>
  </div>
</template>
