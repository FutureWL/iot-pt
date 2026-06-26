<script setup lang="ts">
defineProps<{ title?: string }>()
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">产品管理</h2>
    <div class="page-card" style="min-height: 60vh; display: flex; align-items: center; justify-content: center;">
      <el-empty description="产品 + 物模型 JSON 编辑器(M2 后填充)" />
    </div>
  </div>
</template>
