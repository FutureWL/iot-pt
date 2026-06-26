<script setup lang="ts">
defineProps<{ title?: string }>()
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">历史数据</h2>
    <div class="page-card" style="min-height: 60vh; display: flex; align-items: center; justify-content: center;">
      <el-empty description="TDengine 时序查询 + 图表(M4 后填充)" />
    </div>
  </div>
</template>
