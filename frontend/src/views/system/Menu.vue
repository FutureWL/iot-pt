<script setup lang="ts">
defineProps<{ title?: string }>()
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">菜单管理</h2>
    <div class="page-card" style="min-height: 60vh; display: flex; align-items: center; justify-content: center;">
      <el-empty description="M1 填充" />
    </div>
  </div>
</template>
