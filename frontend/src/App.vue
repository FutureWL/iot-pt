<script setup lang="ts">
// 根组件 — 由 vue-router 接管整个布局
</script>

<template>
  <el-config-provider>
    <router-view />
  </el-config-provider>
</template>

<style>
#app {
  height: 100%;
  width: 100%;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC',
    'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
}
</style>
