<script setup lang="ts">
defineProps<{ title?: string }>()
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">
      通知渠道
    </h2>
    <div
      class="page-card"
      style="min-height: 60vh; display: flex; align-items: center; justify-content: center;"
    >
      <el-empty description="钉钉/微信/Webhook 渠道(M5 后填充)" />
    </div>
  </div>
</template>
