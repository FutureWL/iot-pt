<script setup lang="ts">
defineProps<{ title?: string }>()
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">设备列表</h2>
    <div class="page-card" style="min-height: 60vh; display: flex; align-items: center; justify-content: center;">
      <el-empty description="设备 CRUD、状态、分组、命令下发(M3 后填充)" />
    </div>
  </div>
</template>
