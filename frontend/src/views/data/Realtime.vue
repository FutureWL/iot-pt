<script setup lang="ts">
defineProps<{ title?: string }>()
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">实时数据</h2>
    <div class="page-card" style="min-height: 60vh; display: flex; align-items: center; justify-content: center;">
      <el-empty description="WebSocket 实时推送 + 实时折线图(M4 后填充)" />
    </div>
  </div>
</template>
